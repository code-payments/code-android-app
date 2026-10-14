package com.flipcash.app.messenger.internal

import app.cash.turbine.test
import com.flipcash.shared.chat.UnreadBoundary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The jump-to-bottom badge opens at the unread divider's count, holds there until the viewer
 * drags the transcript, and follows the live count from then on.
 */
class UnreadBadgeTest {

    private val divider = UnreadBoundary.At(readThrough = 10, count = 5)

    private val state = MutableStateFlow(ChatViewModel.State())
    private val live = MutableStateFlow(5)
    private val arrivals = MutableStateFlow(0)
    private val askedAfter = mutableListOf<Long>()

    private fun badge() = unreadBadgeCounts(
        state = state,
        live = live,
        arrivalsAfter = { afterId -> askedAfter += afterId; arrivals },
    )

    private fun dispatch(event: ChatViewModel.Event) =
        state.update { ChatViewModel.stateReducer(event, UnreadDividerLifetime.UntilClose)(it) }

    private fun open(boundary: UnreadBoundary, newestInbound: Long = 15) {
        dispatch(ChatViewModel.Event.UnreadBoundaryResolved(boundary, walkBudget = 5))
        dispatch(ChatViewModel.Event.ArrivalCutoffRecorded(newestInbound))
    }

    @Test
    fun `opens at the divider's count even with unread rows already read on screen`() = runTest {
        open(divider)
        // Three of the five unread rows were on screen at open and the read pointer has moved past them.
        live.value = 2

        badge().test {
            assertEquals(5, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `after a user drag the badge follows the live count`() = runTest {
        open(divider)
        live.value = 2

        badge().test {
            assertEquals(5, awaitItem())

            dispatch(ChatViewModel.Event.UserScrolledTranscript)
            assertEquals(2, awaitItem())

            live.value = 1
            assertEquals(1, awaitItem())
        }
    }

    @Test
    fun `reading rows does not move a frozen badge`() = runTest {
        open(divider)

        badge().test {
            assertEquals(5, awaitItem())

            live.value = 3
            live.value = 0
            expectNoEvents()
        }
    }

    @Test
    fun `no divider means no badge`() = runTest {
        open(UnreadBoundary.None, newestInbound = NO_INBOUND_AT_OPEN)
        live.value = 0

        badge().test {
            assertEquals(0, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `an inbound arrival while frozen adds to the divider's count`() = runTest {
        open(divider)

        badge().test {
            assertEquals(5, awaitItem())

            arrivals.value = 1
            live.value = 6
            assertEquals(6, awaitItem())

            arrivals.value = 2
            live.value = 7
            assertEquals(7, awaitItem())
        }
        assertEquals(listOf(15L), askedAfter.distinct())
    }

    @Test
    fun `history loaded after open below the read pointer is not an arrival`() = runTest {
        // Empty cache at open: no inbound stored, but the member row says the viewer read through 40.
        open(UnreadBoundary.None, newestInbound = arrivalCutoff(readPointer = 40, newestInbound = null))
        live.value = 0

        badge().test {
            assertEquals(0, awaitItem())

            // The history fetch lands rows at or below 40; the DB counts only ids past the cutoff.
            arrivals.value = 0
            expectNoEvents()

            arrivals.value = 1
            live.value = 1
            assertEquals(1, awaitItem())
        }
        assertEquals(listOf(40L), askedAfter.distinct())
    }

    @Test
    fun `the cutoff is the later of the read pointer and the newest inbound`() {
        assertEquals(40L, arrivalCutoff(readPointer = 40, newestInbound = null))
        assertEquals(40L, arrivalCutoff(readPointer = 40, newestInbound = 25))
        assertEquals(55L, arrivalCutoff(readPointer = 40, newestInbound = 55))
        assertEquals(55L, arrivalCutoff(readPointer = null, newestInbound = 55))
    }

    @Test
    fun `with neither a pointer nor inbound at open every later inbound counts as an arrival`() = runTest {
        open(UnreadBoundary.None, newestInbound = arrivalCutoff(readPointer = null, newestInbound = null))
        live.value = 0

        badge().test {
            assertEquals(0, awaitItem())

            arrivals.value = 1
            live.value = 1
            assertEquals(1, awaitItem())
        }
        // Asked for everything past an id below any real one.
        assertEquals(listOf(NO_INBOUND_AT_OPEN), askedAfter.distinct())
    }

    @Test
    fun `an own send that the server confirmed clears the badge`() = runTest {
        open(divider)

        badge().test {
            assertEquals(5, awaitItem())

            // The delegate advances the read pointer to the sent message before it reports success.
            live.value = 0
            dispatch(ChatViewModel.Event.OwnMessageSent)
            assertEquals(0, awaitItem())
        }
    }

    @Test
    fun `the id newest at open is read once, from the resolved open`() = runTest {
        // Before the open resolves there is nothing to count arrivals past.
        badge().test {
            assertEquals(0, awaitItem())
            assertEquals(emptyList(), askedAfter)

            open(divider, newestInbound = 15)
            assertEquals(5, awaitItem())
        }
        assertEquals(listOf(15L), askedAfter.distinct())
    }

    @Test
    fun `history the opening load stores is not an arrival, whatever it brings`() = runTest {
        // Empty cache and no read pointer: the opening sync is what fills the chat.
        val stored = MutableStateFlow(emptyList<Long>())
        val countingBadge = unreadBadgeCounts(
            state = state,
            live = live,
            arrivalsAfter = { afterId -> askedAfter += afterId; stored.map { ids -> ids.count { it > afterId } } },
        )
        dispatch(ChatViewModel.Event.UnreadBoundaryResolved(UnreadBoundary.None, walkBudget = 0))
        live.value = 0

        countingBadge.test {
            assertEquals(0, awaitItem())

            // The sync lands 12 inbound rows before the cutoff is read; none counts.
            stored.value = (1L..12L).toList()
            expectNoEvents()
            assertEquals(emptyList(), askedAfter)

            dispatch(ChatViewModel.Event.ArrivalCutoffRecorded(arrivalCutoff(readPointer = null, newestInbound = 12)))
            expectNoEvents()

            stored.value = stored.value + 13L
            live.value = 1
            assertEquals(1, awaitItem())
        }
    }

    @Test
    fun `a send that clears the boundary does not shrink the frozen badge`() {
        val opened = ChatViewModel.stateReducer(
            ChatViewModel.Event.UnreadBoundaryResolved(divider, walkBudget = 5),
            UnreadDividerLifetime.UntilSend,
        )(ChatViewModel.State())
        val sent = ChatViewModel.stateReducer(ChatViewModel.Event.SendMessage, UnreadDividerLifetime.UntilSend)(opened)

        assertEquals(UnreadBoundary.None, sent.unreadBoundary)
        assertEquals(5, sent.unreadBadgeOpenCount)
        assertEquals(true, sent.unreadBadgeFrozen)
    }
}
