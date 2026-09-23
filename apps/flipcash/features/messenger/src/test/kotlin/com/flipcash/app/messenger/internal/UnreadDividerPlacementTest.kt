package com.flipcash.app.messenger.internal

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.UnreadBoundary
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.MessagePart
import com.flipcash.shared.chat.models.SeparatorConfig
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The shared placement table, which iOS encodes too. The boundary each case passes is the one
 * [com.flipcash.shared.chat.MessagingOperations.resolveUnreadBoundary] resolves for it; the count
 * side of the table is covered against Room in `ChatMessageDaoTest`.
 */
class UnreadDividerPlacementTest {

    private val noon = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun row(id: Long, self: Boolean = false, part: MessagePart? = null) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text("m$id"),
        isFromSelf = self,
        timestamp = noon,
        part = part,
    )

    /** Oldest first, as the table reads; returns the id the divider sits above, or null. */
    private fun dividerAbove(boundary: UnreadBoundary, vararg oldestFirst: ChatListItem.ContentBubble): Long? {
        val newestFirst = oldestFirst.reversed()
        return newestFirst.indices.firstNotNullOfOrNull { i ->
            val newer = newestFirst[i]
            val older = newestFirst.getOrNull(i + 1)
            newer.messageId.takeIf { unreadDividerBetween(newer, older, boundary) }
        }
    }

    @Test
    fun plain() = assertEquals(
        3L,
        dividerAbove(UnreadBoundary.At(2, 2), row(1), row(2, self = true), row(3), row(4)),
    )

    @Test
    fun `nothing unread`() = assertEquals(null, dividerAbove(UnreadBoundary.None, row(1), row(2)))

    @Test
    fun `only own after pointer`() =
        assertEquals(null, dividerAbove(UnreadBoundary.None, row(1), row(2, self = true), row(3, self = true)))

    @Test
    fun `read-through deleted`() = assertEquals(3L, dividerAbove(UnreadBoundary.At(2, 1), row(1), row(3)))

    @Test
    fun `unread tombstone`() = assertEquals(2L, dividerAbove(UnreadBoundary.At(1, 1), row(1), row(2), row(3)))

    @Test
    fun `no self row`() = assertEquals(null, dividerAbove(UnreadBoundary.None, row(1), row(2)))

    @Test
    fun `own message first after pointer`() =
        assertEquals(3L, dividerAbove(UnreadBoundary.At(2, 1), row(1), row(2, self = true), row(3)))

    @Test
    fun `resolving draws nothing`() = assertEquals(null, dividerAbove(UnreadBoundary.Resolving, row(1), row(2)))

    @Test
    fun `every stored message unread puts the divider above the oldest`() =
        assertEquals(1L, dividerAbove(UnreadBoundary.At(0, 2), row(1), row(2)))

    @Test
    fun `rows of one split message never straddle the divider`() {
        val boundary = UnreadBoundary.At(1, 1)
        val leading = row(2, part = MessagePart.Leading)
        val card = row(2, part = MessagePart.Card)
        // Newest first within the message: the card sits under the leading text in reverse layout.
        assertEquals(false, unreadDividerBetween(newer = card, older = leading, boundary = boundary))
        assertEquals(true, unreadDividerBetween(newer = leading, older = row(1), boundary = boundary))
    }

    @Test
    fun `a date on the divider's gap rides on the divider`() {
        val older = row(1).copy(timestamp = noon)
        val newer = row(2).copy(timestamp = Instant.fromEpochMilliseconds(noon.toEpochMilliseconds() + 3 * 86_400_000L))

        assertEquals(
            ChatListItem.UnreadDivider(count = 1, date = newer.timestamp),
            separatorBetween(newer, older, UnreadBoundary.At(1, 1), SeparatorConfig.DayOnly),
        )
        assertEquals(
            ChatListItem.UnreadDivider(count = 1, date = null),
            separatorBetween(newer.copy(timestamp = noon), older, UnreadBoundary.At(1, 1), SeparatorConfig.DayOnly),
        )
        assertEquals(
            ChatListItem.DateSeparator(newer.timestamp),
            separatorBetween(newer, older, UnreadBoundary.None, SeparatorConfig.DayOnly),
        )
    }

    @Test
    fun `counts over 99 read 99+`() {
        assertEquals("1", unreadCountLabel(1))
        assertEquals("99", unreadCountLabel(99))
        assertEquals("99+", unreadCountLabel(100))
    }
}
