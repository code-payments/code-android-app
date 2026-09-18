package com.flipcash.app.messenger.internal

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.screens.components.SwipeToReplyState
import com.flipcash.app.messenger.internal.screens.components.replyGutterFor
import com.flipcash.app.messenger.internal.screens.components.resist
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The geometry of the reply swipe, in pixels at the distances the row is built from: 64 of travel
 * before the drag resists, 48 to arm the reply.
 *
 * Mirrors iOS's `ChatSwipeToReplyTests`. The two implementations share these numbers, so a change
 * here is a change to the gesture on both platforms.
 *
 * Also where the row arms from, which is a layout question rather than a shared one: the gesture
 * starts on the bubble and nowhere else.
 */
class SwipeToReplyTest {

    private val max = 64f
    private val trigger = 48f
    private val gutter = 30.dp
    private val tolerance = 0.01f

    @Test
    fun `a leading-ward drag does not move the row`() {
        assertEquals(0f, resist(-80f, max), tolerance)
    }

    @Test
    fun `up to the maximum the row follows the finger exactly`() {
        assertEquals(30f, resist(30f, max), tolerance)
        assertEquals(max, resist(max, max), tolerance)
    }

    @Test
    fun `past the maximum the row resists but keeps moving`() {
        val near = resist(200f, max)
        val far = resist(500f, max)
        assertTrue(near > max, "the row should still be past its maximum")
        assertTrue(near < 200f, "the row should lag the finger")
        assertTrue(far > near, "a longer drag should still move the row further")
    }

    @Test
    fun `resistance approaches one more maximum of travel and never exceeds it`() {
        assertTrue(resist(5_000f, max) < max * 2)
    }

    @Test
    fun `the affordance rides the row up to its end threshold`() {
        assertEquals(0f, stateAt(40f).affordanceTranslationPx(), tolerance)
        assertEquals(0f, stateAt(max).affordanceTranslationPx(), tolerance)
    }

    @Test
    fun `the affordance holds still while the row runs on past it`() {
        assertEquals(-16f, stateAt(max + 16f).affordanceTranslationPx(), tolerance)
    }

    @Test
    fun `the affordance fills over the run-up to the trigger and caps there`() {
        assertEquals(0.5f, stateAt(trigger / 2).progress(), tolerance)
        assertEquals(1f, stateAt(trigger).progress(), tolerance)
        assertEquals(1f, stateAt(max).progress(), tolerance)
    }

    @Test
    fun `a DM's incoming row arms from its own leading edge`() {
        assertEquals(
            0.dp,
            replyGutterFor(gutter, isFromSelf = false, showsSenderGutter = false),
        )
    }

    @Test
    fun `a group's incoming row does not arm from the avatar column`() {
        assertEquals(
            gutter,
            replyGutterFor(gutter, isFromSelf = false, showsSenderGutter = true),
        )
    }

    @Test
    fun `an outgoing row does not arm from the empty side it is aligned away from`() {
        assertEquals(gutter, replyGutterFor(gutter, isFromSelf = true, showsSenderGutter = false))
        assertEquals(gutter, replyGutterFor(gutter, isFromSelf = true, showsSenderGutter = true))
    }

    private fun stateAt(offset: Float) = SwipeToReplyState(
        modifier = Modifier,
        offsetPx = { offset },
        triggerPx = trigger,
        affordanceEndPx = max,
    )
}
