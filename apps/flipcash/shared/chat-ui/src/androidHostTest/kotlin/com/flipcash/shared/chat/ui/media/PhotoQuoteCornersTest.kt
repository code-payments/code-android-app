package com.flipcash.shared.chat.ui.media

import androidx.compose.ui.unit.dp
import com.flipcash.shared.chat.ui.BubbleCorners
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoQuoteCornersTest {

    @Test
    fun `chip on a rounded photo corner runs parallel to it`() {
        val corners = photoQuoteCorners(photoTopStart = 20.dp, large = 20.dp, small = 4.dp, inset = 8.dp)

        assertEquals(BubbleCorners(12.dp, 12.dp, 12.dp, 12.dp), corners)
    }

    @Test
    fun `chip on a flattened photo corner floors at the small radius`() {
        // A photo grouped under an earlier message from the same sender: its top-start corner is
        // the flattened one, and the chip's nested corner can't go below that.
        val corners = photoQuoteCorners(photoTopStart = 4.dp, large = 20.dp, small = 4.dp, inset = 8.dp)

        assertEquals(BubbleCorners(4.dp, 12.dp, 12.dp, 12.dp), corners)
    }
}
