package com.flipcash.app.messenger.internal.request

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckPlacementTest {

    @Test
    fun `a viewer who owes sees the strip at the newest messages`() {
        assertEquals(DeckPlacement.Strip, deckPlacement(owes = true, atNewest = true))
    }

    @Test
    fun `scrolling away shrinks the strip into the grey pill`() {
        assertEquals(DeckPlacement.Pill, deckPlacement(owes = true, atNewest = false))
    }

    @Test
    fun `a viewer who owes nothing keeps the grey pill`() {
        assertEquals(DeckPlacement.Pill, deckPlacement(owes = false, atNewest = true))
        assertEquals(DeckPlacement.Pill, deckPlacement(owes = false, atNewest = false))
    }
}
