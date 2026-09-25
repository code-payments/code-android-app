package com.flipcash.shared.chat.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Test
import kotlin.test.assertEquals

/** [QuickReactionStripPlacement]: iOS's strip placement, beside the long-pressed bubble. */
class QuickReactionStripPlacementTest {

    private val window = IntSize(1000, 2000)
    private val strip = IntSize(600, 100)

    private fun place(anchor: IntRect, hugsTrailing: Boolean, strip: IntSize = this.strip) =
        QuickReactionStripPlacement.position(
            anchor = anchor,
            window = window,
            strip = strip,
            hugsTrailing = hugsTrailing,
            margin = 40,
            gap = 40,
            minTop = 300,
        )

    @Test
    fun `sits above the bubble, leading edges aligned, for an incoming message`() {
        assertEquals(
            IntOffset(120, 1500 - 40 - 100),
            place(IntRect(left = 120, top = 1500, right = 500, bottom = 1600), hugsTrailing = false),
        )
    }

    @Test
    fun `hugs the trailing edge of an outgoing bubble`() {
        assertEquals(
            IntOffset(960 - 600, 1360),
            place(IntRect(left = 500, top = 1500, right = 960, bottom = 1600), hugsTrailing = true),
        )
    }

    @Test
    fun `keeps the window margin when the bubble runs to the edge`() {
        assertEquals(
            IntOffset(40, 1360),
            place(IntRect(left = 0, top = 1500, right = 400, bottom = 1600), hugsTrailing = false),
        )
        assertEquals(
            IntOffset(1000 - 40 - 600, 1360),
            place(IntRect(left = 500, top = 1500, right = 1000, bottom = 1600), hugsTrailing = true),
        )
    }

    @Test
    fun `an incoming strip that would overflow the trailing margin slides back in`() {
        assertEquals(
            IntOffset(1000 - 40 - 600, 1360),
            place(IntRect(left = 700, top = 1500, right = 900, bottom = 1600), hugsTrailing = false),
        )
    }

    @Test
    fun `goes below the bubble when there is no room above`() {
        assertEquals(
            IntOffset(120, 600 + 40),
            place(IntRect(left = 120, top = 350, right = 500, bottom = 600), hugsTrailing = false),
        )
    }
}
