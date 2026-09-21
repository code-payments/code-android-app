package com.flipcash.app.messenger.internal

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.flipcash.app.messenger.internal.screens.components.ChatTopBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.MessageCapability
import com.flipcash.shared.chat.models.ChatListItem
import com.getcode.navigation.core.CodeNavigator
import com.getcode.opencode.model.core.RandomId
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.Mint
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/**
 * What the selection bar offers is a function of the selected bubble's capabilities alone. A cash
 * bubble is the narrowest case and the one worth pinning: a payment cannot be edited or deleted,
 * and it carries no text to copy, so reply is the whole bar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-xhdpi")
class ChatSelectionBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val cash = ChatListItem.ContentBubble(
        messageId = 1,
        contentIndex = 0,
        content = MessageContent.Cash(
            intentId = RandomId,
            amount = 25.toFiat(),
            mint = Mint.usdf,
        ),
        isFromSelf = true,
        timestamp = Instant.fromEpochSeconds(1_000),
        capabilities = setOf(MessageCapability.Reply),
    )

    @Test
    fun `a selected cash bubble offers reply and nothing else`() {
        composeTestRule.setContent {
            FlipcashPreview {
                ChatTopBar(
                    navigator = mockk<CodeNavigator>(relaxed = true),
                    state = ChatViewModel.State(
                        chatType = ChatType.CONTACT_DM,
                        selection = cash,
                    ),
                        onBarHeightChange = {},
                chatActionHandler = {},
                    dispatch = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("action_reply_message").assertIsDisplayed()
        // Including the overflow: one action fits, so nothing is a menu away either.
        listOf(
            "action_delete_message",
            "action_copy_message",
            "action_edit_message",
            "action_report_message",
            "action_message_overflow",
        ).forEach { composeTestRule.onAllNodesWithTag(it).assertCountEquals(0) }
    }

    // Wider than the class default on purpose. Which actions stay as icons and which collapse into
    // the overflow falls out of the width, and these two tests are about whether report is offered
    // at all — given room for every action, the icon is there or it is not.
    @Test
    @Config(qualifiers = "w800dp-h800dp-xhdpi")
    fun `a message from someone else can be reported`() {
        // The capability set is what `resolveCapabilities` returns for another participant's text;
        // that mapping has its own tests in `:shared:chat`, so this pins only the bar's reading.
        select(
            text(
                isFromSelf = false,
                capabilities = setOf(
                    MessageCapability.Copy,
                    MessageCapability.Reply,
                    MessageCapability.Report,
                ),
            )
        )

        composeTestRule.onNodeWithTag("action_report_message").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h800dp-xhdpi")
    fun `your own message cannot be reported`() {
        select(
            text(
                isFromSelf = true,
                capabilities = setOf(
                    MessageCapability.Copy,
                    MessageCapability.Reply,
                    MessageCapability.Edit,
                    MessageCapability.Delete,
                ),
            )
        )

        composeTestRule.onAllNodesWithTag("action_report_message").assertCountEquals(0)
    }

    private fun text(isFromSelf: Boolean, capabilities: Set<MessageCapability>) =
        ChatListItem.ContentBubble(
            messageId = 2,
            contentIndex = 0,
            content = MessageContent.Text("hello"),
            isFromSelf = isFromSelf,
            timestamp = Instant.fromEpochSeconds(1_000),
            capabilities = capabilities,
        )

    private fun select(bubble: ChatListItem.ContentBubble) {
        composeTestRule.setContent {
            FlipcashPreview {
                ChatTopBar(
                    navigator = mockk<CodeNavigator>(relaxed = true),
                    state = ChatViewModel.State(
                        chatType = ChatType.CONTACT_DM,
                        selection = bubble,
                    ),
                    onBarHeightChange = {},
                    chatActionHandler = {},
                    dispatch = {},
                )
            }
        }
    }
}
