package com.flipcash.shared.chat.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.LocalLinkCardResolution
import com.flipcash.shared.chat.models.splitAroundLinkCard
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.theme.DesignSystem
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Taps on link cards. The card body takes a double tap for the transcript's reaction and waits out
 * the window before a single tap acts; an explicit button inside a card acts on the first tap.
 */
@RunWith(RobolectricTestRunner::class)
class LinkCardTapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val cashLink = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"
    private val inviteLink = "https://app.flipcash.com/chat/6f1c3a9e-2b7d-4e0a-9c55-1d2e3f405162"
    private val chatId = ChatId(ByteArray(16))

    private val cash = LinkCard.Cash(
        url = cashLink,
        start = 0,
        end = cashLink.length,
        entropy = "KNi8pQr1n5hRU65vKJGge3",
        state = LinkCard.Cash.State.Resolved(
            amount = "$5.00",
            claim = LinkCard.Cash.Claim.Claimable,
            token = Token.usdf,
        ),
    )

    private val invite = LinkCard.GroupInvite(
        url = inviteLink,
        start = 0,
        end = inviteLink.length,
        chatId = chatId,
        state = LinkCard.GroupInvite.State.Resolved(
            title = "Bad Boys",
            picture = null,
            memberCount = 3,
            requirement = null,
        ),
    )

    private class HeldResolution : LinkCardResolution {
        override val revision = MutableStateFlow(0)
        override fun peek(card: LinkCard): LinkCard = card
        override suspend fun resolve(card: LinkCard): LinkCard = card
    }

    private fun setCard(
        card: LinkCard,
        onAction: (ChatAction) -> Unit,
        onDoubleClick: () -> Unit = {},
    ) {
        val row = ChatListItem.ContentBubble(
            messageId = 1,
            contentIndex = 0,
            content = MessageContent.Text(card.url),
            isFromSelf = false,
            timestamp = Instant.fromEpochSeconds(1_000),
            linkCard = card,
        ).splitAroundLinkCard().single()
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalChatActionHandler provides onAction,
                    LocalLinkCardResolution provides HeldResolution(),
                ) {
                    ContentBubble(
                        item = row,
                        position = BubblePosition.Solo,
                        onLongClick = {},
                        onDoubleClick = onDoubleClick,
                    )
                }
            }
        }
    }

    private val card = SemanticsMatcher.keyIsDefined(LinkCardShapeKey)

    @Test
    fun `the claim pill acts on the first tap with no double-tap wait`() {
        val actions = mutableListOf<ChatAction>()
        setCard(cash, onAction = { actions += it })
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("Tap to claim").performClick()

        assertEquals(
            listOf<ChatAction>(ChatAction.CashLinkOpened(cash.entropy, cash.url)),
            actions.toList(),
        )
    }

    @Test
    fun `a double tap on the claim pill does not react`() {
        var reacts = 0
        val actions = mutableListOf<ChatAction>()
        setCard(cash, onAction = { actions += it }, onDoubleClick = { reacts++ })
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Tap to claim").performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()

        assertEquals(0, reacts)
    }

    @Test
    fun `a double tap on the cash card outside the pill reacts`() {
        var reacts = 0
        val actions = mutableListOf<ChatAction>()
        setCard(cash, onAction = { actions += it }, onDoubleClick = { reacts++ })
        composeTestRule.waitForIdle()

        composeTestRule.onNode(card, useUnmergedTree = true).performTouchInput {
            doubleClick(Offset(width * 0.2f, height * 0.2f))
        }
        composeTestRule.waitForIdle()

        assertEquals(1, reacts)
        assertEquals(emptyList(), actions.toList())
    }

    @Test
    fun `a single tap on the cash card outside the pill opens it after the double-tap window`() {
        val actions = mutableListOf<ChatAction>()
        setCard(cash, onAction = { actions += it }, onDoubleClick = {})
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNode(card, useUnmergedTree = true).performTouchInput {
            click(Offset(width * 0.2f, height * 0.2f))
        }
        assertEquals(emptyList(), actions.toList())
        composeTestRule.mainClock.advanceTimeBy(1_000)

        assertEquals(
            listOf<ChatAction>(ChatAction.CashLinkOpened(cash.entropy, cash.url)),
            actions.toList(),
        )
    }

    @Test
    fun `the group invite's view button acts on the first tap`() {
        val actions = mutableListOf<ChatAction>()
        setCard(invite, onAction = { actions += it })
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText("View").performClick()

        assertEquals(listOf<ChatAction>(ChatAction.OpenGroup(chatId)), actions.toList())
    }

    @Test
    fun `a double tap on the group invite's body reacts`() {
        var reacts = 0
        setCard(invite, onAction = {}, onDoubleClick = { reacts++ })
        composeTestRule.waitForIdle()

        composeTestRule.onNode(card, useUnmergedTree = true).performTouchInput {
            doubleClick(Offset(width * 0.5f, height * 0.1f))
        }
        composeTestRule.waitForIdle()

        assertEquals(1, reacts)
    }
}
