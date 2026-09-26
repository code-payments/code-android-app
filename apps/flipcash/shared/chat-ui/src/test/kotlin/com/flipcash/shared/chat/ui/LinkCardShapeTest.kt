package com.flipcash.shared.chat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.splitAroundLinkCard
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A link card is drawn in the outline a text bubble would have at the same place in its run, so
 * a card squares off against its neighbours the way the bubbles around it do.
 */
@RunWith(RobolectricTestRunner::class)
class LinkCardShapeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val cashLink = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"
    private val inviteLink = "https://app.flipcash.com/chat/6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162"

    private fun cardRow(card: LinkCard, isFromSelf: Boolean) = ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = MessageContent.Text(card.url),
        isFromSelf = isFromSelf,
        timestamp = Instant.fromEpochSeconds(1_000),
        linkCard = card,
    ).splitAroundLinkCard().single()

    private val cash = LinkCard.Cash(
        url = cashLink,
        start = 0,
        end = cashLink.length,
        entropy = "KNi8pQr1n5hRU65vKJGge3",
        state = LinkCard.Cash.State.Unresolved,
    )

    private val invite = LinkCard.GroupInvite(
        url = inviteLink,
        start = 0,
        end = inviteLink.length,
        chatId = ChatId(ByteArray(16)),
        state = LinkCard.GroupInvite.State.Unavailable,
    )

    private val hasShape = SemanticsMatcher.keyIsDefined(LinkCardShapeKey)

    /** Every position, from both sides, for [card]: the shape drawn and the one a bubble takes. */
    private fun assertCardShapesMatchBubbles(card: LinkCard) {
        val cases = BubblePosition.entries.flatMap { position ->
            listOf(true, false).map { isFromSelf -> position to isFromSelf }
        }
        val expected = arrayOfNulls<Shape>(cases.size)

        composeTestRule.setContent {
            DesignSystem {
                Column {
                    cases.forEachIndexed { index, (position, isFromSelf) ->
                        expected[index] = bubbleShape(position, isFromSelf)
                        ContentBubble(item = cardRow(card, isFromSelf), position = position)
                    }
                }
            }
        }

        val drawn = composeTestRule.onAllNodes(hasShape, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.config[LinkCardShapeKey] }

        assertEquals(cases.size, drawn.size)
        cases.forEachIndexed { index, case ->
            assertEquals(expected[index], drawn[index], "shape at $case")
        }
    }

    @Test
    fun `a cash card takes the bubble's corners at every position`() {
        assertCardShapesMatchBubbles(cash)
    }

    @Test
    fun `a group invite card takes the bubble's corners at every position`() {
        assertCardShapesMatchBubbles(invite)
    }

    @Test
    fun `positions in a run give a card different corners`() {
        // Guards the comparison above: if every position resolved to the same outline, matching
        // bubbleShape would prove nothing.
        val shapes = mutableListOf<Shape>()
        composeTestRule.setContent {
            DesignSystem {
                BubblePosition.entries.forEach { shapes += bubbleShape(it, isFromSelf = true) }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(BubblePosition.entries.size, shapes.distinct().size)
    }
}
