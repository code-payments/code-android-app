package com.flipcash.shared.chat.models

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.reactions.ReactionPill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A message holding a carded link draws as up to three rows: the text before the link, the card,
 * the text after it. These pin the order, what is dropped, and the keys the list tells them apart by.
 */
class SplitAroundLinkCardTest {

    private val link = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"

    private fun split(
        text: String,
        content: MessageContent = MessageContent.Text(text),
        reactionPills: List<ReactionPill> = emptyList(),
    ) =
        ChatListItem.ContentBubble(
            messageId = 42,
            contentIndex = 0,
            content = content,
            isFromSelf = true,
            timestamp = Instant.fromEpochSeconds(1_000),
            isEdited = true,
            linkCard = LinkCard.Cash(
                url = link,
                start = text.indexOf(link),
                end = text.indexOf(link) + link.length,
                entropy = "KNi8pQr1n5hRU65vKJGge3",
                state = LinkCard.Cash.State.Unresolved,
            ),
            reactionPills = reactionPills,
        ).splitAroundLinkCard()

    @Test
    fun `text on both sides splits into three rows in source order`() {
        val rows = split("before $link after")

        assertEquals(
            listOf(MessagePart.Leading, MessagePart.Card, MessagePart.Trailing),
            rows.map { it.part },
        )
        assertEquals(listOf("before", null, "after"), rows.map { it.partText })
        assertEquals(listOf(true, false, false), rows.map { it.isFirstRow })
        assertEquals(listOf(false, false, true), rows.map { it.isLastRow })
    }

    @Test
    fun `only the card row carries the card`() {
        val rows = split("before $link after")

        assertEquals(listOf(false, true, false), rows.map { it.linkCard != null })
    }

    @Test
    fun `every row keeps the whole message for copy and edit`() {
        val text = "before $link after"
        val rows = split(text)

        assertEquals(listOf(text, text, text), rows.map { it.plainText })
        assertEquals(1, rows.map { it.messageKey }.distinct().size)
    }

    @Test
    fun `text and a link carry the reaction pills on the last row only`() {
        val pills = listOf(ReactionPill(emoji = "👍", count = 2, selfReacted = true, pending = false))
        val rows = split("Join my group $link", reactionPills = pills)

        assertEquals(listOf(MessagePart.Leading, MessagePart.Card), rows.map { it.part })
        assertEquals(listOf(emptyList(), pills), rows.map { it.reactionPills })
    }

    @Test
    fun `row ids are the message id plus the part`() {
        val rows = split("before $link after")

        assertEquals(
            listOf("42-0#leading", "42-0#card", "42-0#trailing"),
            rows.map { it.itemKey },
        )
    }

    @Test
    fun `a link alone is one card row`() {
        val row = split(link).single()

        assertEquals(MessagePart.Card, row.part)
        assertEquals(true, row.isFirstRow)
        assertEquals(true, row.isLastRow)
    }

    @Test
    fun `whitespace-only segments are dropped`() {
        assertEquals(
            listOf(MessagePart.Leading, MessagePart.Card),
            split("look  $link \n  ").map { it.part },
        )
        assertEquals(
            listOf(MessagePart.Card, MessagePart.Trailing),
            split(" \n$link  thanks").map { it.part },
        )
    }

    @Test
    fun `punctuation touching the link goes with the card`() {
        assertEquals(listOf("hi", null), split("hi $link.").map { it.partText })
        assertEquals(listOf(MessagePart.Card), split("($link)").map { it.part })
        assertEquals(listOf(null, "then lunch"), split("$link, then lunch").map { it.partText })
        assertEquals(listOf(null, "enjoy"), split("\"$link\" enjoy").map { it.partText })
    }

    @Test
    fun `punctuation past a space stays with its words`() {
        assertEquals(listOf("is this you?", null), split("is this you? $link").map { it.partText })
        assertEquals(listOf("look:", null), split("look: $link").map { it.partText })
    }

    @Test
    fun `a segment of only punctuation is dropped`() {
        assertEquals(listOf(MessagePart.Card), split("$link !!").map { it.part })
        assertEquals(listOf(MessagePart.Card), split("... $link").map { it.part })
    }

    @Test
    fun `symbols and emoji are kept`() {
        assertEquals(listOf(null, "🎉"), split("$link 🎉").map { it.partText })
        assertEquals(listOf(null, "\$5"), split("$link \$5").map { it.partText })
    }

    @Test
    fun `a split on punctuation still copies the whole message`() {
        val text = "hi $link."

        assertEquals(listOf(text, text), split(text).map { it.plainText })
    }

    @Test
    fun `other links stay in the text row they fell in`() {
        val rows = split("$link and https://flipcash.app")

        assertEquals("and https://flipcash.app", rows.last().partText)
    }

    @Test
    fun `a reply splits on its text`() {
        val text = "see $link"
        val rows = split(text, MessageContent.Reply(repliedMessageId = 7, content = listOf(MessageContent.Text(text))))

        assertEquals(listOf(MessagePart.Leading, MessagePart.Card), rows.map { it.part })
    }

    @Test
    fun `a bubble with no card is one row`() {
        val row = ChatListItem.ContentBubble(
            messageId = 1,
            contentIndex = 0,
            content = MessageContent.Text("hi"),
            isFromSelf = true,
            timestamp = Instant.fromEpochSeconds(0),
        ).splitAroundLinkCard().single()

        assertNull(row.part)
        assertEquals("1-0", row.itemKey)
    }
}
