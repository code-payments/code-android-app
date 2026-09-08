package com.flipcash.shared.chat.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Two bubbles install a tap target of their own — the cash bubble, and the citation panel inside a
 * reply — and that is why their long-press needs asserting: a gesture a bubble handles never
 * reaches the row behind it, so the transcript's selection gesture is the bubble's to report or to
 * lose.
 */
@RunWith(RobolectricTestRunner::class)
class BubbleGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val cash = ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        // A blank token name leaves the header off, so the bubble renders without loading an icon.
        content = MessageContent.Cash(
            intentId = RandomId,
            amount = 25.toFiat(),
            mint = Mint.usdf,
        ),
        isFromSelf = true,
        timestamp = Instant.fromEpochSeconds(1_000),
        capabilities = setOf(MessageCapability.Reply),
    )

    private val reply = cash.copy(
        messageId = 2,
        content = MessageContent.Reply(
            repliedMessageId = 1,
            content = listOf(MessageContent.Text("on its way")),
        ),
        quote = ChatQuote(
            messageId = 1,
            authorName = "Ada",
            snippet = ChatQuoteSnippet.Text("did you send it?"),
            accent = null,
            nameAccent = null,
        ),
    )

    private fun setBubble(
        item: ChatListItem.ContentBubble = cash,
        interactive: Boolean = true,
        onLongClick: (() -> Unit)? = null,
        onAction: (ChatAction) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalChatActionHandler provides onAction) {
                DesignSystem {
                    ContentBubble(
                        item = item,
                        position = BubblePosition.Solo,
                        interactive = interactive,
                        onLongClick = onLongClick,
                    )
                }
            }
        }
    }

    @Test
    fun `long-pressing a cash bubble reports the press`() {
        var longPresses = 0
        setBubble(onLongClick = { longPresses++ })

        composeTestRule.onNodeWithText("You sent").performTouchInput { longClick() }

        composeTestRule.runOnIdle { assertEquals(1, longPresses) }
    }

    @Test
    fun `tapping a cash bubble still opens its token`() {
        val actions = mutableListOf<ChatAction>()
        setBubble(onLongClick = {}, onAction = { actions += it })

        composeTestRule.onNodeWithText("You sent").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf<ChatAction>(ChatAction.ViewToken(Mint.usdf)), actions.toList())
        }
    }

    @Test
    fun `long-pressing a reply's citation reports the press`() {
        var longPresses = 0
        setBubble(item = reply, onLongClick = { longPresses++ })

        composeTestRule.onNodeWithTag("bubble_reply_quote").performTouchInput { longClick() }

        composeTestRule.runOnIdle { assertEquals(1, longPresses) }
    }

    @Test
    fun `tapping a reply's citation still jumps to the quoted message`() {
        val actions = mutableListOf<ChatAction>()
        setBubble(item = reply, onLongClick = {}, onAction = { actions += it })

        composeTestRule.onNodeWithTag("bubble_reply_quote").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf<ChatAction>(ChatAction.JumpToMessage(1)), actions.toList())
        }
    }

    @Test
    fun `a cash bubble behind the selection backdrop reports nothing`() {
        var longPresses = 0
        val actions = mutableListOf<ChatAction>()
        setBubble(interactive = false, onLongClick = { longPresses++ }, onAction = { actions += it })

        composeTestRule.onNodeWithText("You sent").performTouchInput { longClick() }
        composeTestRule.onNodeWithText("You sent").performClick()

        composeTestRule.runOnIdle {
            assertEquals(0, longPresses)
            assertEquals(emptyList<ChatAction>(), actions.toList())
        }
    }
}
