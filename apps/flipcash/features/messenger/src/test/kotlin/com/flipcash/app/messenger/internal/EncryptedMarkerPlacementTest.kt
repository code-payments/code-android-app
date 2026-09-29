package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.MessagePart
import com.flipcash.shared.chat.models.SeparatorConfig
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class EncryptedMarkerPlacementTest {

    private val noon = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun row(id: Long, at: Instant = noon, part: MessagePart? = null) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text("m$id"),
        isFromSelf = false,
        timestamp = at,
        part = part,
    )

    private fun between(
        newer: ChatListItem.ContentBubble,
        older: ChatListItem.ContentBubble?,
        oldestEncryptedId: Long?,
        boundary: UnreadBoundary = UnreadBoundary.None,
    ) = separatorBetween(newer, older, boundary, SeparatorConfig.DayOnly, oldestEncryptedId)

    /** Oldest first; the ids the marker sits above. */
    private fun markedAbove(oldestEncryptedId: Long?, vararg oldestFirst: ChatListItem.ContentBubble): List<Long> {
        val newestFirst = oldestFirst.reversed()
        return newestFirst.indices.mapNotNull { i ->
            val newer = newestFirst[i]
            newer.messageId.takeIf {
                between(newer, newestFirst.getOrNull(i + 1), oldestEncryptedId) is ChatListItem.EncryptedMarker
            }
        }
    }

    @Test
    fun `the marker sits above the oldest encrypted message, below older plaintext`() =
        assertEquals(listOf(3L), markedAbove(3, row(1), row(2), row(3), row(4)))

    @Test
    fun `no encrypted message, no marker`() = assertEquals(emptyList(), markedAbove(null, row(1), row(2)))

    @Test
    fun `a transcript encrypted from the start has the marker at the head, carrying the date`() {
        assertEquals(listOf(1L), markedAbove(1, row(1), row(2)))
        val marker = assertIs<ChatListItem.EncryptedMarker>(between(row(1), older = null, oldestEncryptedId = 1))
        assertEquals(ChatListItem.DateSeparator(noon), marker.above)
    }

    @Test
    fun `a day change in the same gap is drawn above the marker`() {
        val marker = assertIs<ChatListItem.EncryptedMarker>(
            between(row(2, at = noon + 1.days), row(1), oldestEncryptedId = 2),
        )
        assertEquals(ChatListItem.DateSeparator(noon + 1.days), marker.above)
    }

    @Test
    fun `the same day keeps the marker alone`() =
        assertNull(assertIs<ChatListItem.EncryptedMarker>(between(row(2), row(1), oldestEncryptedId = 2)).above)

    @Test
    fun `the unread divider in the same gap is drawn above the marker`() {
        val marker = assertIs<ChatListItem.EncryptedMarker>(
            between(row(2), row(1), oldestEncryptedId = 2, boundary = UnreadBoundary.At(1, 1)),
        )
        assertEquals(ChatListItem.UnreadDivider(1), marker.above)
        assertEquals(true, marker.holdsUnreadDivider)
    }

    @Test
    fun `the marker never splits the rows of one message`() {
        val card = row(2, part = MessagePart.Card)
        val leading = row(2, part = MessagePart.Leading)
        assertNull(between(newer = card, older = leading, oldestEncryptedId = 2))
        assertIs<ChatListItem.EncryptedMarker>(between(newer = leading, older = row(1), oldestEncryptedId = 2))
    }
}
