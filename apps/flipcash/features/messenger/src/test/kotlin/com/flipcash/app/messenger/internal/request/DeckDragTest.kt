package com.flipcash.app.messenger.internal.request

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stack's vertical drag: down cycles, up is only an elastic pull (spec rows 26 and 27). */
class DeckDragTest {

    private val height = 200f
    private val pull = height * ELASTIC_PULL

    /** Drags from rest by each delta in turn, as the draggable does. */
    private fun drag(vararg deltas: Float, canCycle: Boolean = true): Float =
        deltas.fold(0f) { value, delta -> deckDragTarget(value, delta, canCycle, height) }

    // 27
    @Test
    fun `a drag up gives a little and no further`() {
        val small = drag(-20f)
        assertTrue(small < 0f && small > -20f)
        assertEquals(-pull, drag(*FloatArray(40) { -50f }), 0.001f)
    }

    // 27
    @Test
    fun `a drag up let go springs back rather than cycling`() {
        val up = drag(-300f)
        assertFalse(deckDragCycles(up, velocity = -5_000f, canCycle = true, heightPx = height))
        assertFalse(deckDragCycles(up, velocity = 5_000f, canCycle = true, heightPx = height))
    }

    @Test
    fun `a drag up then back down follows the finger down from where it was`() {
        val up = drag(-100f)
        val back = deckDragTarget(up, 100f, canCycle = true, heightPx = height)
        assertTrue(back > up)
        assertTrue(back <= 0f)
    }

    @Test
    fun `a drag down follows the finger and cycles past 40 percent`() {
        assertEquals(90f, drag(40f, 50f), 0.001f)
        assertTrue(deckDragCycles(drag(90f), velocity = 0f, canCycle = true, heightPx = height))
        assertFalse(deckDragCycles(drag(70f), velocity = 0f, canCycle = true, heightPx = height))
    }

    @Test
    fun `a fling down cycles from a short drag`() {
        assertTrue(deckDragCycles(drag(10f), velocity = 5_000f, canCycle = true, heightPx = height))
    }

    @Test
    fun `past one card height the front card resists`() {
        val atHeight = drag(height)
        val further = deckDragTarget(atHeight, 40f, canCycle = true, heightPx = height)
        assertTrue(further - atHeight < 40f)
    }

    @Test
    fun `one request only pulls a little either way and never cycles`() {
        assertEquals(pull, drag(*FloatArray(40) { 50f }, canCycle = false), 0.001f)
        assertEquals(-pull, drag(*FloatArray(40) { -50f }, canCycle = false), 0.001f)
        assertFalse(deckDragCycles(pull, velocity = 5_000f, canCycle = false, heightPx = height))
    }
}
