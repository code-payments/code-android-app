package com.flipcash.app.menu.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A drag has to move the card by every pixel the finger covered.
 *
 * Touch events arrive in batches, one batch per frame, so a slow frame hands the drag several
 * deltas in a row before any of them can be applied — which is exactly when the animation is
 * already struggling and the card can least afford to lose ground. Each delta is applied in a
 * coroutine of its own, and `Animatable` serialises those through a mutex that cancels whatever it
 * finds running, so "the batch adds up" is a property worth holding on to rather than assuming: a
 * drag that read its base value and wrote it back across a cancellation point would lose the
 * cancelled delta, and the card would stick under the finger and then lurch.
 *
 * The dispatcher here queues rather than running inline, which is what reproduces the batch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TipCardDragTest {

    @Test
    fun `a delta a frame moves the card by all of them`() = runTest {
        val expansion = TipCardExpansion(CoroutineScope(StandardTestDispatcher(testScheduler)))

        repeat(5) {
            expansion.dragBy(0.1f)
            advanceUntilIdle()
        }

        assertEquals(0.5f, expansion.progress, Tolerance)
    }

    @Test
    fun `five deltas in one frame's batch move the card by all of them`() = runTest {
        val expansion = TipCardExpansion(CoroutineScope(StandardTestDispatcher(testScheduler)))

        repeat(5) { expansion.dragBy(0.1f) }
        advanceUntilIdle()

        assertEquals(0.5f, expansion.progress, Tolerance)
    }

    @Test
    fun `a drag past either end stops there`() = runTest {
        val expansion = TipCardExpansion(CoroutineScope(StandardTestDispatcher(testScheduler)))

        repeat(20) { expansion.dragBy(0.1f) }
        advanceUntilIdle()
        assertEquals(1f, expansion.progress, Tolerance)

        repeat(40) { expansion.dragBy(-0.1f) }
        advanceUntilIdle()
        assertEquals(0f, expansion.progress, Tolerance)
    }

    @Test
    fun `pushing the card down past full screen gives, a quarter of the way`() = runTest {
        val expansion = expandedCard()

        expansion.dragBy(0.4f)
        advanceUntilIdle()

        assertEquals(1f, expansion.progress, Tolerance)
        assertEquals(0.1f, expansion.overdrag, Tolerance)
    }

    @Test
    fun `a finger that turns around gives back all it pushed before the card moves`() = runTest {
        val expansion = expandedCard()

        expansion.dragBy(0.4f)
        expansion.dragBy(-0.3f)
        advanceUntilIdle()

        // Still home, a quarter of what is left of the push behind it. Unwinding the overdrag at
        // its resisted size instead would leave the card moving home while the finger is still
        // below where it pushed from.
        assertEquals(1f, expansion.progress, Tolerance)
        assertEquals(0.025f, expansion.overdrag, Tolerance)

        expansion.dragBy(-0.2f)
        advanceUntilIdle()

        assertEquals(0.9f, expansion.progress, Tolerance)
        assertEquals(0f, expansion.overdrag, Tolerance)
    }

    @Test
    fun `the slack the recogniser swallowed is handed back on the first delta only`() = runTest {
        val expansion = expandedCard()

        expansion.startDrag(slack = 0.05f)
        expansion.dragBy(-0.1f)
        advanceUntilIdle()
        assertEquals(0.85f, expansion.progress, Tolerance)

        expansion.dragBy(-0.1f)
        advanceUntilIdle()
        assertEquals(0.75f, expansion.progress, Tolerance)
    }

    /** A card dragged all the way out, which is the only state the swipe exists in. */
    private suspend fun TestScope.expandedCard(): TipCardExpansion {
        val expansion = TipCardExpansion(CoroutineScope(StandardTestDispatcher(testScheduler)))
        expansion.dragBy(1f)
        advanceUntilIdle()
        return expansion
    }
}

private const val Tolerance = 1e-4f
