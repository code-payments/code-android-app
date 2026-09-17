package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.screens.components.RowGap
import com.flipcash.app.messenger.internal.screens.components.rowGapBelow
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.shared.chat.models.SeparatorConfig
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * How far apart two rows sit.
 *
 * The list is `reverseLayout`, so the gap a row owns is the one under it — to the *newer* message,
 * `peek(index - 1)`. A change of author opens the widest gap, which is the space the next run's
 * name and picture go in.
 */
class RowGapTest {

    private val config = SeparatorConfig.DayOnly
    private val noon = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val alice =
        SenderIdentity(userId = listOf<Byte>(1), displayName = "Alice", picture = null)
    private val bob = SenderIdentity(userId = listOf<Byte>(2), displayName = "Bob", picture = null)

    private fun bubble(
        sender: SenderIdentity?,
        isFromSelf: Boolean = false,
        at: Instant = noon,
    ) = ChatListItem.ContentBubble(
        messageId = 1L,
        contentIndex = 0,
        content = MessageContent.Text("hi"),
        isFromSelf = isFromSelf,
        timestamp = at,
        sender = sender,
    )

    @Test
    fun `two members of a group are a run apart`() {
        assertEquals(RowGap.Wide, rowGapBelow(bubble(alice), bubble(bob), config))
    }

    @Test
    fun `the viewer and a member are a run apart`() {
        val own = bubble(sender = null, isFromSelf = true)
        assertEquals(RowGap.Wide, rowGapBelow(bubble(alice), own, config))
        assertEquals(RowGap.Wide, rowGapBelow(own, bubble(alice), config))
    }

    @Test
    fun `one member's consecutive messages stay tight`() {
        assertEquals(RowGap.Tight, rowGapBelow(bubble(alice), bubble(alice), config))
    }

    @Test
    fun `one member's messages open up outside the grouping window`() {
        val later = bubble(alice, at = noon + 5.minutes)
        assertEquals(RowGap.Normal, rowGapBelow(bubble(alice), later, config))
    }

    @Test
    fun `the viewer's own consecutive messages stay tight`() {
        val own = bubble(sender = null, isFromSelf = true)
        assertEquals(RowGap.Tight, rowGapBelow(own, own, config))
    }

    @Test
    fun `a separator takes the normal gap on either side`() {
        val separator = ChatListItem.DateSeparator(noon)
        assertEquals(RowGap.Normal, rowGapBelow(bubble(alice), separator, config))
        assertEquals(RowGap.Normal, rowGapBelow(separator, bubble(alice), config))
    }

    @Test
    fun `the newest row has nothing under it`() {
        assertEquals(RowGap.Tight, rowGapBelow(bubble(alice), below = null, config = config))
    }
}
