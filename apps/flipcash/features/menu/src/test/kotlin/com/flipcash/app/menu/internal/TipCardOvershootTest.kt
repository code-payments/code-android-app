package com.flipcash.app.menu.internal

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A released swipe has to bounce, and not much.
 *
 * The spring carries the release velocity into the settle, and a flick can hand it far more speed
 * than the spring would ever have picked up on its own — enough that it sails a fifth of the travel
 * past the end it was aiming for, which on the resting end lifts the card clear out of its slot and
 * up under the status bar. Capping that velocity is what keeps the bounce to a settle; the cap is
 * only meaningful if it leaves a bounce there at all, so both bounds are held here. iOS meets
 * neither, having no swipe: its spring always starts from a standstill.
 *
 * Driven a frame at a time off a [BroadcastFrameClock], so these are the values the card is
 * actually drawn at, not a reading of where the animation was aimed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TipCardOvershootTest {

    @Test
    fun `no flick bounces the card far past full screen`() = runTest {
        for (from in ReleasePoints) {
            val overshoot = flingTravel(from, velocity = HardFlick).endInclusive - 1f

            assertTrue(
                overshoot <= MaxBounce,
                "released at $from, the card bounced $overshoot past full screen",
            )
        }
    }

    @Test
    fun `no flick lifts the card far out of its slot`() = runTest {
        for (from in ReleasePoints) {
            val overshoot = -flingTravel(from, velocity = -HardFlick).start

            assertTrue(
                overshoot <= MaxBounce,
                "released at $from, the card bounced $overshoot above its resting place",
            )
        }
    }

    @Test
    fun `a flick does still bounce`() = runTest {
        assertTrue(flingTravel(from = 0.5f, velocity = HardFlick).endInclusive > 1f + Tolerance)
        assertTrue(flingTravel(from = 0.5f, velocity = -HardFlick).start < -Tolerance)
    }

    @Test
    fun `a flick still comes to rest where it was aimed`() = runTest {
        assertTrue(flingTravel(from = 0.5f, velocity = HardFlick).endInclusive >= 1f - Tolerance)
        assertTrue(flingTravel(from = 0.5f, velocity = -HardFlick).start <= Tolerance)
    }
}

/** Every tenth of the way out, since where a flick is released changes how far it carries past. */
private val ReleasePoints = (1..9).map { it / 10f }

/**
 * The bounce budget, as a fraction of the card's travel — about 10dp of settle on a 1080x2424
 * display, against the 33dp an uncapped flick spent.
 */
private const val MaxBounce = 0.06f

/**
 * Settles the card from [from] at [velocity] and reports the range it was drawn across on the way
 * to rest — which is wider than the range between its two ends if the spring overshoots one.
 */
private suspend fun TestScope.flingTravel(
    from: Float,
    velocity: Float,
): ClosedFloatingPointRange<Float> {
    val clock = BroadcastFrameClock()
    val expansion = TipCardExpansion(CoroutineScope(StandardTestDispatcher(testScheduler) + clock))

    expansion.dragBy(from)
    advanceUntilIdle()
    expansion.settle(velocity, FlingThreshold)
    advanceUntilIdle()

    var lowest = expansion.progress
    var highest = lowest
    repeat(FramesToSettle) { frame ->
        clock.sendFrame((frame + 1) * NanosPerFrame)
        advanceUntilIdle()
        lowest = minOf(lowest, expansion.progress)
        highest = maxOf(highest, expansion.progress)
    }
    return lowest..highest
}

/**
 * Android's maximum fling velocity (8000 px/s) over the ~560 px the card travels between its slot
 * and the middle of a 1080x2424 display — the fastest a release can ever hand the spring.
 */
private const val HardFlick = 14.2f

/** [MinFlingVelocity] over that same travel. */
private const val FlingThreshold = 0.58f

private const val NanosPerFrame = 16_666_667L

/** Two seconds of frames — several times over what this spring needs to come to rest. */
private const val FramesToSettle = 120

private const val Tolerance = 1e-3f
