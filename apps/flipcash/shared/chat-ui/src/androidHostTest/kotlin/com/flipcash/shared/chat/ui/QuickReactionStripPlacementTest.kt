package com.flipcash.shared.chat.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Test
import kotlin.test.assertEquals

/** [QuickReactionStripPlacement]: the strip above the lifted bubble, hugging the sender's side. */
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
    fun `stays above a bubble that has no room yet, held at the top`() {
        // The transcript scrolls the bubble down to make room; until then the strip waits at minTop
        // rather than flipping below and back.
        assertEquals(
            IntOffset(120, 300),
            place(IntRect(left = 120, top = 350, right = 500, bottom = 600), hugsTrailing = false),
        )
    }

    @Test
    fun `goes below a bubble too tall to leave room for it above`() {
        // 2000 - 300 (minTop) - 40 (gap) - 100 (strip) = 1560 is the tallest bubble it can sit over.
        assertEquals(
            IntOffset(120, 1700 + 40),
            place(IntRect(left = 120, top = 100, right = 500, bottom = 1700), hugsTrailing = false),
        )
    }

    @Test
    fun `a bubble exactly as tall as the room left keeps the strip above`() {
        assertEquals(
            IntOffset(120, 300),
            place(IntRect(left = 120, top = 440, right = 500, bottom = 2000), hugsTrailing = false),
        )
    }

    @Test
    fun `a message split around its card is one anchor spanning its rows`() {
        // "Join my group <link>": a text bubble over a wider card, both outgoing.
        val text = IntRect(left = 700, top = 1000, right = 960, bottom = 1100)
        val card = IntRect(left = 400, top = 1120, right = 960, bottom = 1600)

        val anchor = QuickReactionStripPlacement.messageBounds(listOf(card, text))

        assertEquals(IntRect(left = 400, top = 1000, right = 960, bottom = 1600), anchor)
        // Above the text row, not between the text and the card.
        assertEquals(IntOffset(960 - 600, 1000 - 40 - 100), place(anchor!!, hugsTrailing = true))
    }

    @Test
    fun `a split message too tall for the strip above puts it below its last row`() {
        val text = IntRect(left = 120, top = 200, right = 400, bottom = 300)
        val card = IntRect(left = 120, top = 320, right = 680, bottom = 1800)

        val anchor = QuickReactionStripPlacement.messageBounds(listOf(text, card))!!

        assertEquals(IntOffset(120, 1800 + 40), place(anchor, hugsTrailing = false))
    }

    @Test
    fun `no rows measured yet is no anchor`() {
        assertEquals(null, QuickReactionStripPlacement.messageBounds(emptyList()))
    }
}
