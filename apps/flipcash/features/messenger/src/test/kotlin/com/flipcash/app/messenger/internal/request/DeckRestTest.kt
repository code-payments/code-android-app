package com.flipcash.app.messenger.internal.request

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckRestTest {

    private val dinner = listOf<Byte>(1)
    private val sunday = listOf<Byte>(2)

    @Test
    fun `the strip shows while the viewer owes`() {
        assertEquals(DeckPlacement.Strip, DeckRest().owing(setOf(dinner)).host)
    }

    @Test
    fun `the pill shows while the viewer owes nothing`() {
        assertEquals(DeckPlacement.Pill, DeckRest().owing(emptySet()).host)
    }

    // Test 26: scrolled away, the strip collapses into the pill and stays there.
    @Test
    fun `a collapsed strip stays in the pill while the same requests are owed`() {
        val collapsed = DeckRest().owing(setOf(dinner)).collapsed()

        assertEquals(DeckPlacement.Pill, collapsed.owing(setOf(dinner)).host)
    }

    // Test 27: reopening the chat starts a fresh rest, so the strip is back.
    @Test
    fun `a new visit shows the strip again while the viewer still owes`() {
        DeckRest().owing(setOf(dinner)).collapsed()

        assertEquals(DeckPlacement.Strip, DeckRest().owing(setOf(dinner)).host)
    }

    // Test 28: a request the strip hasn't shown brings it back.
    @Test
    fun `a new owed request brings a collapsed strip back`() {
        val collapsed = DeckRest().owing(setOf(dinner)).collapsed()

        assertEquals(DeckPlacement.Strip, collapsed.owing(setOf(dinner, sunday)).host)
    }

    @Test
    fun `paying one of two owed requests leaves a collapsed strip in the pill`() {
        val collapsed = DeckRest().owing(setOf(dinner, sunday)).collapsed()

        assertEquals(DeckPlacement.Pill, collapsed.owing(setOf(sunday)).host)
    }

    @Test
    fun `paying the last owed share moves the strip to the pill`() {
        val strip = DeckRest().owing(setOf(dinner))

        assertEquals(DeckPlacement.Pill, strip.owing(emptySet()).host)
    }
}
