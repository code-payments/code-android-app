package com.flipcash.app.messenger.internal.request

import com.flipcash.app.messenger.internal.pins.PinSlot
import com.flipcash.app.messenger.internal.pins.pinSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C7: a swipe up on the strip docks it into the pill without a scroll, through the collapse a scroll drives. */
class DeckDockTest {

    private val dinner = listOf<Byte>(1)
    private val sunday = listOf<Byte>(2)

    private fun strip() = DeckHostState(setOf(dinner)).apply { stripPx = 100f }

    @Test
    fun `a swipe up partway collapses without docking`() {
        val deck = strip()

        deck.dragDock(-40f)

        assertTrue(deck.collapsing)
        assertEquals(DeckPlacement.Strip, deck.rest.host)
    }

    @Test
    fun `a swipe back down undoes the collapse`() {
        val deck = strip()

        deck.dragDock(-40f)
        deck.dragDock(60f)

        assertFalse(deck.collapsing)
        assertEquals(DeckPlacement.Strip, deck.rest.host)
    }

    // C7a: mid-swipe the pill is growing into the trailing slot, so the pin button is already gone.
    @Test
    fun `a swipe partway up takes the pin button out of the top bar`() {
        val deck = strip()

        deck.dragDock(-30f)

        assertEquals(
            PinSlot.None,
            pinSlot(hasPins = true, deckRest = deck.rest.host, collapsing = deck.collapsing),
        )
    }

    // C7b: docked, the pill holds the trailing slot and the pin bar comes back.
    @Test
    fun `a swipe the strip's full height docks it and gives pins the bar`() {
        val deck = strip()

        deck.dragDock(-100f)

        assertEquals(DeckPlacement.Pill, deck.rest.host)
        assertEquals(PinSlot.Bar, pinSlot(hasPins = true, deckRest = deck.rest.host))
    }

    @Test
    fun `the dock holds while the same requests are owed`() {
        val deck = strip()
        deck.dragDock(-100f)

        deck.owing(setOf(dinner), hasDeck = true)

        assertEquals(DeckPlacement.Pill, deck.rest.host)
    }

    @Test
    fun `a new request brings the docked strip back`() {
        val deck = strip()
        deck.dragDock(-100f)

        deck.owing(setOf(dinner, sunday), hasDeck = true)

        assertEquals(DeckPlacement.Strip, deck.rest.host)
        assertEquals(PinSlot.TopBarButton, pinSlot(hasPins = true, deckRest = deck.rest.host))
    }

    @Test
    fun `nothing docks while the deck rests as the pill`() {
        val deck = DeckHostState(emptySet()).apply { stripPx = 100f }

        deck.dragDock(-100f)

        assertFalse(deck.collapsing)
    }
}
