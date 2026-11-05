package com.flipcash.shared.chat.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A row that fills its width sizes against a transcript no wider than 440dp; a text bubble sizes to
 * its text and keeps the full transcript's ceiling.
 */
class BubbleWidthCapTest {

    private val inset = 16.dp

    /** Pixel 7a: 412dp transcript, under the cap. */
    private val phoneRow = 412.dp - inset * 2

    /** An unfolded foldable: 673dp transcript. */
    private val wideRow = 673.dp - inset * 2

    private val photo = MessageContent.Media(items = emptyList(), caption = null)
    private val text = MessageContent.Text("hello")
    private val widget = MessageContent.Widget(WidgetContent.Unsupported)

    @Test
    fun `a phone's widths are unchanged`() {
        assertDp(412.dp * 0.78f, bubbleMaxWidth(photo, phoneRow, inset))
        assertDp(412.dp * 0.78f, bubbleMaxWidth(text, phoneRow, inset))
        assertDp(phoneRow * 0.64f, cashCardWidth(phoneRow, inset))
    }

    @Test
    fun `a photo on a wide screen sizes against 440dp`() {
        assertDp(440.dp * 0.78f, bubbleMaxWidth(photo, wideRow, inset))
    }

    @Test
    fun `a photo reply on a wide screen sizes against 440dp`() {
        val reply = MessageContent.Reply(repliedMessageId = 1, content = listOf(photo))
        assertDp(440.dp * 0.78f, bubbleMaxWidth(reply, wideRow, inset))
    }

    @Test
    fun `a widget on a wide screen sizes against 440dp`() {
        assertDp(440.dp * 0.78f, bubbleMaxWidth(widget, wideRow, inset))
    }

    @Test
    fun `a cash card on a wide screen sizes against 440dp`() {
        assertDp((440.dp - inset * 2) * 0.64f, cashCardWidth(wideRow, inset))
    }

    @Test
    fun `a link card on a wide screen sizes against 440dp`() {
        assertDp(440.dp * 0.78f, transcriptBubbleMaxWidth(wideRow, inset, fillsWidth = true))
    }

    @Test
    fun `a text bubble on a wide screen keeps the full width`() {
        assertDp(673.dp * 0.78f, bubbleMaxWidth(text, wideRow, inset))
        val reply = MessageContent.Reply(repliedMessageId = 1, content = listOf(text))
        assertDp(673.dp * 0.78f, bubbleMaxWidth(reply, wideRow, inset))
    }

    private fun assertDp(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.01f)
}
