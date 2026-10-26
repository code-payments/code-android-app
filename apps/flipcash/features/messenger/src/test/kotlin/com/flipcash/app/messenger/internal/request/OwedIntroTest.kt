package com.flipcash.app.messenger.internal.request

import org.junit.Assert.assertEquals
import org.junit.Test

class OwedIntroTest {

    private val a = listOf<Byte>(1)
    private val b = listOf<Byte>(2)

    @Test
    fun `a new owed request opens the deck on the first open only`() {
        val first = requestsToIntroduce(listOf(a), shown = emptySet(), screenReader = false, reducedMotion = false)
        assertEquals(listOf(a), first)
        val second = requestsToIntroduce(listOf(a), shown = first.toSet(), screenReader = false, reducedMotion = false)
        assertEquals(emptyList<List<Byte>>(), second)
    }

    @Test
    fun `several new owed requests open it once, in deck order`() {
        assertEquals(listOf(b, a), requestsToIntroduce(listOf(b, a), emptySet(), screenReader = false, reducedMotion = false))
    }

    @Test
    fun `a request already shown is left out`() {
        assertEquals(listOf(b), requestsToIntroduce(listOf(a, b), setOf(a), screenReader = false, reducedMotion = false))
    }

    @Test
    fun `nothing opens with a screen reader or reduced motion`() {
        assertEquals(emptyList<List<Byte>>(), requestsToIntroduce(listOf(a), emptySet(), screenReader = true, reducedMotion = false))
        assertEquals(emptyList<List<Byte>>(), requestsToIntroduce(listOf(a), emptySet(), screenReader = false, reducedMotion = true))
    }
}
