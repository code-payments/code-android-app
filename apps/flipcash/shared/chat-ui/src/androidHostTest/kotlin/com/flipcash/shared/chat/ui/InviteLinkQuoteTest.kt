package com.flipcash.shared.chat.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.alerts.ReactionAlertContent
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCard.Message.Body
import com.flipcash.shared.chat.models.LinkCard.Message.State
import com.flipcash.services.models.chat.BlobAccessContext
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock

/**
 * The places outside a bubble that quote a message's text read an invite link as "Invite link":
 * its fragment is the group's key.
 */
@RunWith(RobolectricTestRunner::class)
class InviteLinkQuoteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val secret = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val invite = "https://app.flipcash.com/chat/invite#s=$secret"

    private fun assertNoSecret() {
        composeTestRule.onAllNodesWithText(secret, substring = true).assertCountEquals(0)
    }

    private fun quote(snippet: ChatQuoteSnippet) = ChatQuote(
        messageId = 1,
        authorName = "Ada",
        snippet = snippet,
        accent = null,
        nameAccent = null,
    )

    @Test
    fun `the reply strip labels an invite link in quoted text`() {
        composeTestRule.setContent {
            DesignSystem { ComposerReplyStrip(quote(ChatQuoteSnippet.Text("join $invite now")), onDismiss = {}) }
        }
        composeTestRule.onNodeWithText("join Invite link now").assertExists()
        assertNoSecret()
    }

    @Test
    fun `the reply strip labels an invite link in a photo caption`() {
        composeTestRule.setContent {
            DesignSystem {
                ComposerReplyStrip(quote(ChatQuoteSnippet.Photo(caption = invite, rendition = null, sealed = false, redacted = false)), onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("Invite link", substring = true).assertExists()
        assertNoSecret()
    }

    @Test
    fun `the reaction pill labels an invite link in its quote`() {
        val content = ReactionAlertContent(
            chatId = ChatId(ByteArray(16)),
            messageId = 1,
            kind = ReactionAlertContent.Kind.Reaction,
            chatName = "Ballers",
            quote = invite,
            actorNames = listOf("Ada"),
            emojis = listOf("👍"),
        )
        composeTestRule.setContent { DesignSystem { ReactionAlertPill(content, animate = false) } }
        composeTestRule.onNodeWithText("Invite link", substring = true).assertExists()
        assertNoSecret()
    }

    @Test
    fun `a quoted-message card labels an invite link in its excerpt`() {
        val card = LinkCard.Message(
            url = "https://app.flipcash.com/chat/x/1",
            start = 0,
            end = 0,
            chatId = ChatId(ByteArray(16)),
            messageId = 1,
            isGroup = true,
            state = State.Resolved(
                source = LinkCard.Message.Source(
                    title = "Ballers",
                    kind = LinkCard.Message.Kind.PublicGroup,
                    picture = null,
                    pictureAccess = BlobAccessContext.ChatProfile(ChatId(ByteArray(16))),
                ),
                message = LinkCard.Message.Excerpt(
                    author = "Ada",
                    isFromSelf = false,
                    sentAt = Clock.System.now(),
                    body = Body.Text("come $invite"),
                ),
                reactionCount = 0,
            ),
        )
        composeTestRule.setContent {
            DesignSystem { MessageLinkCard(card, shape = RoundedCornerShape(8), onOpen = null) }
        }
        composeTestRule.onNodeWithText("come Invite link", substring = true).assertExists()
        assertNoSecret()
    }
}
