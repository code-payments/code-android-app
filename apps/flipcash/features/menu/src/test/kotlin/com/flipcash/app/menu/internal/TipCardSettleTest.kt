package com.flipcash.app.menu.internal

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where a swipe on the expanded tip card lands when the finger lifts.
 *
 * The gesture drags the card back up towards its slot on the "You" tab, so the release has to
 * decide between putting it away and springing it back out — and it has to agree with what the
 * hand just did. A flick counts on its own, however short; anything slower goes to the nearer end.
 */
class TipCardSettleTest {

    /** A velocity well inside the threshold: a drag that was drifting, not thrown. */
    private val slow = FlingThreshold / 4

    @Test
    fun `a slow release falls to whichever end it is nearer`() {
        assertFalse(settle(progress = 0.2f, velocity = slow))
        assertFalse(settle(progress = 0.49f, velocity = -slow))
        assertTrue(settle(progress = 0.51f, velocity = -slow))
        assertTrue(settle(progress = 0.9f, velocity = slow))
    }

    @Test
    fun `an upward flick closes the card however little of it was dragged`() {
        // The whole point of a flick: barely moved, but thrown at the slot it came out of.
        assertFalse(settle(progress = 0.98f, velocity = -FlingThreshold))
    }

    @Test
    fun `a downward flick puts the card back even from almost closed`() {
        // Changed their mind mid-swipe — the card belongs back on screen, not shut.
        assertTrue(settle(progress = 0.02f, velocity = FlingThreshold))
    }

    @Test
    fun `a card held exactly half way stays open`() {
        // Ties go to the state the user asked for; putting it away needs a decision, not a draw.
        assertTrue(settle(progress = 0.5f, velocity = 0f))
    }

    @Test
    fun `a motionless card is left where the tap put it`() {
        assertTrue(settle(progress = 1f, velocity = 0f))
        assertFalse(settle(progress = 0f, velocity = 0f))
    }

    private fun settle(progress: Float, velocity: Float) =
        settlesExpanded(progress, velocity, FlingThreshold)

    private companion object {
        /** Stand-in for the density-derived threshold the screen computes; the units cancel. */
        const val FlingThreshold = 2f
    }
}
