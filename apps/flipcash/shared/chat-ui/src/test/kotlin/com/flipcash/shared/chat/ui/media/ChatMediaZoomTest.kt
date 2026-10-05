package com.flipcash.shared.chat.ui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMediaZoomTest {

    private val container = Size(400f, 800f)

    @Test
    fun fitIsNotZoomed() {
        assertTrue(ChatMediaZoom.isFit(1f))
        assertFalse(ChatMediaZoom.isFit(2.5f))
    }

    @Test
    fun fittedSizePreservesAspect() {
        assertEquals(Size(400f, 200f), ChatMediaZoom.fittedSize(container, Size(800f, 400f)))
        assertEquals(container, ChatMediaZoom.fittedSize(container, Size.Zero))
    }

    @Test
    fun panStaysInsideTheImageEdges() {
        val fitted = Size(400f, 200f)
        // At 4x the image is 1600x800: 600 of horizontal slack each way, none vertically.
        val clamped = ChatMediaZoom.clampOffset(Offset(5_000f, 5_000f), 4f, container, fitted)
        assertEquals(Offset(600f, 0f), clamped)
    }

    @Test
    fun zoomHoldsThePointUnderTheFocus() {
        val focus = Offset(300f, 500f)
        val offset = ChatMediaZoom.offsetAfterZoom(Offset.Zero, focus, 1f, 2f, Offset.Zero, container)
        val center = Offset(200f, 400f)
        // The image point under the focus before is (focus - center); after, it sits at
        // (that * scale) + offset and must land back on the focus.
        assertEquals(focus - center, (focus - center) * 2f + offset)
    }

    @Test
    fun transformClampsScale() {
        val state = ChatMediaZoomState().apply { container = this@ChatMediaZoomTest.container }
        state.transform(Offset(200f, 400f), Offset.Zero, 10f)
        assertEquals(4f, state.scale)
        state.transform(Offset(200f, 400f), Offset.Zero, 0.01f)
        assertEquals(1f, state.scale)
    }
}
