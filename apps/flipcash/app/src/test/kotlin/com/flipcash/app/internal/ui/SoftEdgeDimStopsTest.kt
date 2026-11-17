package com.flipcash.app.internal.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoftEdgeDimStopsTest {

    private val leadIn = 29.dp
    private val pill = 64.dp
    private val belowPill = 36.dp
    private val height = leadIn + pill + belowPill

    @Test
    fun `the dim starts clear at the top of the edge and is strongest at the bottom`() {
        val stops = softEdgeDimStops(leadIn, pill, belowPill)

        assertEquals(0f to 0f, stops.first())
        assertEquals(1f, stops.last().first)
        assertEquals(stops.maxOf { it.second }, stops.last().second)
    }

    @Test
    fun `the dim's stops sit at the pill's top, middle and foot`() {
        val fractions = softEdgeDimStops(leadIn, pill, belowPill).map { it.first }

        assertEquals(
            listOf(0f, leadIn / height, (leadIn + pill / 2) / height, (leadIn + pill) / height, 1f),
            fractions,
        )
    }

    @Test
    fun `the dim only deepens toward the bottom`() {
        val stops = softEdgeDimStops(leadIn, pill, belowPill)

        stops.zipWithNext().forEach { (above, below) ->
            assertTrue(below.first > above.first, "stops out of order: $above then $below")
            assertTrue(below.second >= above.second, "dim lightens from $above to $below")
        }
    }

    @Test
    fun `an edge with no height still gives a valid gradient`() {
        val fractions = softEdgeDimStops(0.dp, 0.dp, 0.dp).map { it.first }

        assertTrue(fractions.all { it in 0f..1f }, "fractions out of range: $fractions")
    }
}
