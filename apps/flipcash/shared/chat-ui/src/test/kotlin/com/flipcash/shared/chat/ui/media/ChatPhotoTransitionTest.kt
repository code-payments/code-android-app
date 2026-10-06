package com.flipcash.shared.chat.ui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatPhotoTransitionTest {
    private val container = Size(1200f, 2400f)
    private val bubble = Rect(600f, 400f, 1100f, 750f)
    private val open = Rect(0f, 600f, 1200f, 1800f)

    @Test
    fun `progress zero sits on the bubble and one on the anchor`() {
        val start = ChatPhotoTransition.frame(bubble, ChatPhotoCorners.uniform(48f), open, anchorPull = 0f, progress = 0f)
        assertEquals(bubble, start.rect)
        assertEquals(ChatPhotoCorners.uniform(48f), start.corners)
        assertEquals(0f, start.backdrop)
        val end = ChatPhotoTransition.frame(bubble, ChatPhotoCorners.uniform(48f), open, anchorPull = 0f, progress = 1f)
        assertEquals(open, end.rect)
        assertEquals(ChatPhotoCorners.uniform(0f), end.corners)
        assertEquals(1f, end.backdrop)
        assertEquals(1f, end.chromeAlpha)
    }

    @Test
    fun `halfway is the midpoint of both rects and corners`() {
        val mid = ChatPhotoTransition.frame(bubble, ChatPhotoCorners.uniform(48f), open, anchorPull = 0f, progress = 0.5f)
        assertEquals(Rect(300f, 500f, 1150f, 1275f), mid.rect)
        assertEquals(ChatPhotoCorners.uniform(24f), mid.corners)
        assertEquals(0.5f, mid.backdrop)
    }

    @Test
    fun `a pulled anchor keeps its corners and dims the backdrop`() {
        val pulled = ChatPhotoTransition.frame(bubble, ChatPhotoCorners.uniform(48f), open, anchorPull = 1f, progress = 1f)
        assertEquals(ChatPhotoCorners.uniform(48f), pulled.corners)
        assertEquals(ChatPhotoTransition.MIN_DRAG_BACKDROP, pulled.backdrop)
        assertEquals(0f, pulled.chromeAlpha)
    }

    @Test
    fun `without a bubble the photo fades and shrinks about its own center`() {
        val gone = ChatPhotoTransition.frame(null, ChatPhotoCorners.uniform(48f), open, anchorPull = 0f, progress = 0f)
        assertEquals(0f, gone.contentAlpha)
        assertEquals(open.center, gone.rect.center)
        assertEquals(open.width * ChatPhotoTransition.DETACHED_SCALE, gone.rect.width)
        val shown = ChatPhotoTransition.frame(null, ChatPhotoCorners.uniform(48f), open, anchorPull = 0f, progress = 1f)
        assertEquals(1f, shown.contentAlpha)
        assertEquals(open, shown.rect)
    }

    @Test
    fun `each corner starts on the bubble's own corner and opens square`() {
        val tail = ChatPhotoCorners(topStart = 48f, topEnd = 48f, bottomEnd = 12f, bottomStart = 48f)
        val start = ChatPhotoTransition.frame(bubble, tail, open, anchorPull = 0f, progress = 0f)
        assertEquals(tail, start.corners)
        val mid = ChatPhotoTransition.frame(bubble, tail, open, anchorPull = 0f, progress = 0.5f)
        assertEquals(ChatPhotoCorners(24f, 24f, 6f, 24f), mid.corners)
        val end = ChatPhotoTransition.frame(bubble, tail, open, anchorPull = 0f, progress = 1f)
        assertEquals(ChatPhotoCorners.uniform(0f), end.corners)
    }

    @Test
    fun `the photo is clipped to the transcript at the bubble and to the screen when open`() {
        val transcript = Rect(0f, 300f, 1200f, 2100f)
        val screen = Rect(Offset.Zero, container)
        val corners = ChatPhotoCorners.uniform(48f)
        val start = ChatPhotoTransition.frame(bubble, corners, open, 0f, 0f, transcript, container)
        assertEquals(transcript, start.clip)
        val end = ChatPhotoTransition.frame(bubble, corners, open, 0f, 1f, transcript, container)
        assertEquals(screen, end.clip)
        // In flight the photo already passes under the bars, so it is behind them well before it lands.
        val mid = ChatPhotoTransition.frame(bubble, corners, open, 0f, 0.5f, transcript, container)
        assertEquals(transcript, mid.clip)
        val leaving = ChatPhotoTransition.frame(bubble, corners, open, 0f, 0.9f, transcript, container)
        assertEquals(150f, leaving.clip!!.top, absoluteTolerance = 0.5f)
        assertEquals(2250f, leaving.clip!!.bottom, absoluteTolerance = 0.5f)
    }

    @Test
    fun `without a transcript or a bubble nothing is clipped`() {
        val corners = ChatPhotoCorners.uniform(48f)
        assertEquals(null, ChatPhotoTransition.frame(bubble, corners, open, 0f, 0f).clip)
        val transcript = Rect(0f, 300f, 1200f, 2100f)
        assertEquals(null, ChatPhotoTransition.frame(null, corners, open, 0f, 0f, transcript, container).clip)
    }

    @Test
    fun `progress outside zero to one is clamped`() {
        assertEquals(bubble, ChatPhotoTransition.frame(bubble, ChatPhotoCorners.uniform(0f), open, 0f, -0.2f).rect)
        assertEquals(open, ChatPhotoTransition.frame(bubble, ChatPhotoCorners.uniform(0f), open, 0f, 1.3f).rect)
    }

    @Test
    fun `a pull shrinks the photo about its center and follows the finger`() {
        val none = ChatPhotoTransition.draggedRect(open, Offset.Zero, 0f)
        assertEquals(open, none)
        val full = ChatPhotoTransition.draggedRect(open, Offset(100f, 400f), 1f)
        assertEquals(open.width * ChatPhotoTransition.MIN_DRAG_SCALE, full.width)
        assertEquals(open.center + Offset(100f, 400f), full.center)
    }

    @Test
    fun `pull progress reaches one at half the container height in any direction`() {
        assertEquals(0f, ChatPhotoTransition.dragProgress(Offset.Zero, 2400f))
        assertEquals(0.5f, ChatPhotoTransition.dragProgress(Offset(0f, 600f), 2400f))
        assertEquals(1f, ChatPhotoTransition.dragProgress(Offset(-1200f, 0f), 2400f))
        assertEquals(1f, ChatPhotoTransition.dragProgress(Offset(0f, 5000f), 2400f))
        assertEquals(0f, ChatPhotoTransition.dragProgress(Offset(0f, 5000f), 0f))
    }

    @Test
    fun `a short slow release springs back`() {
        assertFalse(ChatPhotoTransition.shouldDismiss(Offset(0f, 200f), Offset(0f, 300f), 2400f, 3f))
    }

    @Test
    fun `a long release dismisses`() {
        assertTrue(ChatPhotoTransition.shouldDismiss(Offset(0f, 400f), Offset.Zero, 2400f, 3f))
        assertTrue(ChatPhotoTransition.shouldDismiss(Offset(-300f, -300f), Offset.Zero, 2400f, 3f))
    }

    @Test
    fun `a downward fling dismisses even from a short pull`() {
        assertTrue(ChatPhotoTransition.shouldDismiss(Offset(0f, 80f), Offset(0f, 3000f), 2400f, 3f))
    }

    @Test
    fun `a fling that is too slow or upward does not`() {
        assertFalse(ChatPhotoTransition.shouldDismiss(Offset(0f, 80f), Offset(0f, 2000f), 2400f, 3f))
        assertFalse(ChatPhotoTransition.shouldDismiss(Offset(0f, -80f), Offset(0f, 3000f), 2400f, 3f))
    }

    @Test
    fun `a bubble that is off screen cannot be flown to`() {
        assertTrue(ChatPhotoTransition.sourceUsable(bubble, container))
        assertTrue(ChatPhotoTransition.sourceUsable(Rect(100f, -100f, 600f, 100f), container))
        assertFalse(ChatPhotoTransition.sourceUsable(Rect(100f, -500f, 600f, -10f), container))
        assertFalse(ChatPhotoTransition.sourceUsable(Rect(100f, 2500f, 600f, 2900f), container))
        assertFalse(ChatPhotoTransition.sourceUsable(Rect.Zero, container))
    }

    @Test
    fun `the open rect fits the photo inside the container`() {
        val wide = ChatPhotoTransition.fittedRect(container, Size(4000f, 2000f))
        assertEquals(Rect(0f, 900f, 1200f, 1500f), wide)
        val tall = ChatPhotoTransition.fittedRect(container, Size(1000f, 4000f))
        assertEquals(Rect(300f, 0f, 900f, 2400f), tall)
        assertEquals(Rect(Offset.Zero, container), ChatPhotoTransition.fittedRect(container, Size.Zero))
    }
}
