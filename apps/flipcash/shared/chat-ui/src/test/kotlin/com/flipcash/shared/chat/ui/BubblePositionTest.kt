package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.SenderIdentity
import com.flipcash.shared.chat.models.SeparatorConfig
import com.flipcash.shared.chat.models.splitAroundLinkCard
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * How a group's bubbles are cornered: each member's run is shaped on its own, First at the top
 * through Last at the bottom, and a change of member restarts it.
 *
 * The list is held newest-first, as the paging snapshot holds it — index + 1 is the bubble drawn
 * above, index - 1 the one drawn below.
 */
class BubblePositionTest {

    private val alice =
        SenderIdentity(userId = listOf<Byte>(1), displayName = "Alice", picture = null)
    private val bob = SenderIdentity(userId = listOf<Byte>(2), displayName = "Bob", picture = null)
    private val start = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun bubble(
        id: Long,
        secondsIn: Long,
        sender: SenderIdentity? = null,
        senderId: List<Byte>? = null,
        text: String = "m$id",
    ) = ChatListItem.ContentBubble(
        messageId = id,
        contentIndex = 0,
        content = MessageContent.Text(text),
        isFromSelf = false,
        timestamp = start + secondsIn.seconds,
        sender = sender,
        senderId = senderId,
    )

    private fun positionsOf(items: List<ChatListItem.ContentBubble>) =
        items.mapIndexed { index, item ->
            bubblePositionOf(index, item, items, SeparatorConfig.DayOnly)
        }

    @Test
    fun `a run is cornered on its own and restarts at a change of sender`() {
        val items = listOf(
            bubble(4, secondsIn = 30, sender = bob),
            bubble(3, secondsIn = 20, sender = bob),
            bubble(2, secondsIn = 10, sender = alice),
            bubble(1, secondsIn = 0, sender = alice),
        )

        assertEquals(
            listOf(
                BubblePosition.Last,  // bob, newest — bottom of his run
                BubblePosition.First, // bob, top of his run
                BubblePosition.Last,  // alice, bottom of hers
                BubblePosition.First, // alice, oldest
            ),
            positionsOf(items),
        )
    }

    @Test
    fun `two members read as two runs before their profiles resolve`() {
        // What the first frame of a group transcript looks like: the profile map has not arrived,
        // so no bubble carries a `sender` yet. Grouping them by the resolved profile merged every
        // member into one run and cornered it as one.
        val items = listOf(
            bubble(4, secondsIn = 30, senderId = bob.userId),
            bubble(3, secondsIn = 20, senderId = bob.userId),
            bubble(2, secondsIn = 10, senderId = alice.userId),
            bubble(1, secondsIn = 0, senderId = alice.userId),
        )

        assertEquals(
            listOf(
                BubblePosition.Last,
                BubblePosition.First,
                BubblePosition.Last,
                BubblePosition.First,
            ),
            positionsOf(items),
        )
    }

    @Test
    fun `a bare emoji breaks the run around it`() {
        // One sender the whole way, close enough together to group. Without the break, the two
        // words either side would corner as one run through the emoji and square themselves
        // against a bubble that is not drawn.
        val items = listOf(
            bubble(3, secondsIn = 20, sender = alice),
            bubble(2, secondsIn = 10, sender = alice, text = "\uD83D\uDE00"),
            bubble(1, secondsIn = 0, sender = alice),
        )

        assertEquals(
            listOf(
                BubblePosition.Solo, // newest word, closed against the emoji above it
                BubblePosition.Solo, // the emoji, which draws no corners either way
                BubblePosition.Solo, // oldest word, closed against the emoji below it
            ),
            positionsOf(items),
        )
    }

    @Test
    fun `a run still forms on the far side of an emoji`() {
        val items = listOf(
            bubble(4, secondsIn = 30, sender = alice),
            bubble(3, secondsIn = 20, sender = alice),
            bubble(2, secondsIn = 10, sender = alice, text = "\uD83D\uDE00"),
            bubble(1, secondsIn = 0, sender = alice),
        )

        assertEquals(
            listOf(
                BubblePosition.Last,
                BubblePosition.First,
                BubblePosition.Solo,
                BubblePosition.Solo,
            ),
            positionsOf(items),
        )
    }

    @Test
    fun `a card row breaks the run around it, as a bare emoji does`() {
        val link = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"
        val text = "before $link after"
        val carded = bubble(2, secondsIn = 10, senderId = alice.userId, text = text).copy(
            linkCard = LinkCard.Cash(
                url = link,
                start = text.indexOf(link),
                end = text.indexOf(link) + link.length,
                entropy = "KNi8pQr1n5hRU65vKJGge3",
                state = LinkCard.Cash.State.Unresolved,
            ),
        )
        // Newest-first, as the view model emits a split message: its bottom row first.
        val items = listOf(bubble(3, secondsIn = 20, senderId = alice.userId)) +
            carded.splitAroundLinkCard().asReversed() +
            bubble(1, secondsIn = 0, senderId = alice.userId)

        assertEquals(
            listOf(
                BubblePosition.Last,  // newest, grouped under the trailing row
                BubblePosition.First, // trailing, its run closed above by the card
                BubblePosition.Solo,  // card, in no run
                BubblePosition.Last,  // leading, its run closed below by the card
                BubblePosition.First, // oldest, grouped over the leading row
            ),
            positionsOf(items),
        )
    }
}
