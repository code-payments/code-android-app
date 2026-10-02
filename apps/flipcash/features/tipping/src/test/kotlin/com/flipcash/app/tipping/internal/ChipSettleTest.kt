package com.flipcash.app.tipping.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipSettleTest {

    private val chip = 100f

    private fun settle(pastPark: Float, velocity: Float = 0f, fromTop: Boolean = true) =
        chipSettle(pastPark = pastPark, chipHeight = chip, velocity = velocity, pullStartedAtTop = fromTop)

    @Test
    fun `a slow release with more than half the chips on screen reveals them`() {
        assertEquals(ChipSettle.Reveal, settle(pastPark = -60f))
    }

    @Test
    fun `a slow release with half or less on screen parks`() {
        assertEquals(ChipSettle.Park, settle(pastPark = -50f))
        assertEquals(ChipSettle.Park, settle(pastPark = -10f))
    }

    @Test
    fun `a quick flick down from a short pull reveals`() {
        // -10 - 500 * 0.1 = -60: past half once projected.
        assertEquals(ChipSettle.Reveal, settle(pastPark = -10f, velocity = 500f))
    }

    @Test
    fun `a flick back into the list from a partial reveal parks`() {
        // -60 + 300 * 0.1 = -30: not past park, so the spring brings it to park.
        assertEquals(ChipSettle.Park, settle(pastPark = -60f, velocity = -300f))
    }

    @Test
    fun `a hard flick back into the list leaves the list's own fling`() {
        assertEquals(ChipSettle.None, settle(pastPark = -20f, velocity = -2_000f))
    }

    @Test
    fun `a gesture that began mid-list never reveals`() {
        assertEquals(ChipSettle.ClampedFling, settle(pastPark = 400f, velocity = 8_000f, fromTop = false))
        assertEquals(ChipSettle.Park, settle(pastPark = -80f, fromTop = false))
    }

    @Test
    fun `a fling toward the top from inside the list is clamped`() {
        assertEquals(ChipSettle.ClampedFling, settle(pastPark = 400f, velocity = 300f))
    }

    @Test
    fun `a release at rest or moving into the list is left alone`() {
        assertEquals(ChipSettle.None, settle(pastPark = 0f))
        assertEquals(ChipSettle.None, settle(pastPark = 400f, velocity = -3_000f))
    }

    @Test
    fun `with the chips not laid out a fling toward the top is still clamped`() {
        assertEquals(
            ChipSettle.ClampedFling,
            chipSettle(pastPark = 2_000f, chipHeight = 0f, velocity = 5_000f, pullStartedAtTop = false),
        )
        assertEquals(
            ChipSettle.None,
            chipSettle(pastPark = 2_000f, chipHeight = 0f, velocity = -5_000f, pullStartedAtTop = false),
        )
    }
}
