package com.flipcash.app.messenger.internal.request

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckRotationTest {

    @Test
    fun `a swipe sends the front card to the back`() {
        assertEquals(listOf(2, 3, 1), listOf(1, 2, 3).rotated(1))
    }

    @Test
    fun `cycling past the end comes back round`() {
        assertEquals(listOf(1, 2, 3), listOf(1, 2, 3).rotated(3))
        assertEquals(listOf(3, 1, 2), listOf(1, 2, 3).rotated(5))
    }

    @Test
    fun `one card does not move`() {
        assertEquals(listOf(1), listOf(1).rotated(4))
    }
}
