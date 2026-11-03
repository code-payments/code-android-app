package com.flipcash.shared.chat.share

import com.flipcash.services.models.chat.ChatId
import org.junit.Test
import kotlin.test.assertEquals

class ShareRecipientSelectionTest {

    private fun chat(n: Int) = ChatId(ByteArray(32) { n.toByte() })

    @Test
    fun `picks keep their order and a pick again drops the chat`() {
        var selection = ShareRecipientSelection()
        selection = selection.toggle(chat(2)).first
        selection = selection.toggle(chat(1)).first
        assertEquals(listOf(chat(2), chat(1)), selection.ids)

        val (after, result) = selection.toggle(chat(2))
        assertEquals(ShareRecipientSelection.Toggle.Deselected, result)
        assertEquals(listOf(chat(1)), after.ids)
    }

    @Test
    fun `the sixth chat is refused`() {
        var selection = ShareRecipientSelection()
        repeat(MAX_SHARE_RECIPIENTS) { selection = selection.toggle(chat(it)).first }

        val (after, result) = selection.toggle(chat(99))

        assertEquals(5, MAX_SHARE_RECIPIENTS)
        assertEquals(ShareRecipientSelection.Toggle.LimitReached, result)
        assertEquals(selection, after)
        assertEquals(ShareRecipientSelection.Toggle.Deselected, after.toggle(chat(0)).second)
    }
}
