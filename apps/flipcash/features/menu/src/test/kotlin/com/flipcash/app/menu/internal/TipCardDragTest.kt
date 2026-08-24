package com.flipcash.app.menu.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
}

private const val Tolerance = 1e-4f
