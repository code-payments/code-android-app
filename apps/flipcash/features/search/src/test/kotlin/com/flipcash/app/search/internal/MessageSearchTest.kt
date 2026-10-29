package com.flipcash.app.search.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.WidgetContent
import org.junit.Test
import kotlin.test.assertEquals

class MessageSearchTest {

    private fun find(query: String, vararg messages: StoredMessage) =
        searchMessages(messages.toList(), SearchMatching.query(query)!!, inviteLinkLabel = "Invite link").map { it.messageId }

    @Test
    fun `text, captions and replies are searched, newest first`() {
        assertEquals(
            listOf(3L, 2L, 1L),
            find(
                "jeff",
                stored(1, text("ask Jeff")),
                stored(3, MessageContent.Reply(1, listOf(text("jeff said yes")))),
                stored(2, MessageContent.Media(emptyList(), text("Jeff's photo"))),
            ),
        )
    }

    @Test
    fun `a shared profile widget is searched by its username`() {
        assertEquals(
            listOf(1L),
            find("jeffx", stored(1, MessageContent.Widget(WidgetContent.ShareProfile("jeffx")))),
        )
    }

    @Test
    fun `unsupported content is never a hit`() {
        assertEquals(emptyList(), find("jeff", stored(1, MessageContent.Unsupported)))
    }
}
