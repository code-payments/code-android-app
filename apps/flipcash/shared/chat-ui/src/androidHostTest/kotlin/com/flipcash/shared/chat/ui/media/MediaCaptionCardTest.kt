package com.flipcash.shared.chat.ui.media

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatAction
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.models.LinkCardResolution
import com.flipcash.shared.chat.models.LocalChatActionHandler
import com.flipcash.shared.chat.models.LocalLinkCardResolution
import com.flipcash.shared.chat.models.PhotoMessageContext
import com.flipcash.shared.chat.models.splitAroundLinkCard
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.ContentBubble
import com.flipcash.shared.chat.ui.JUMBO_EMOJI_TAG
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdf
import com.getcode.theme.DesignSystem
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A photo's caption is read and drawn as a text message's body is. These pin the three places it
 * used to differ: a Flipcash card under the caption, a caption that is only a link, and one that is
 * only emoji.
 */
@RunWith(RobolectricTestRunner::class)
class MediaCaptionCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val cashLink = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"

    private val photo = MessageContent.Media(
        items = listOf(
            MediaItem(
                listOf(
                    MediaItemRendition(
                        role = MediaItemRendition.Role.ORIGINAL,
                        blobId = BlobId(ByteArray(32) { 1 }),
                        blob = BlobMetadata(
                            mimeType = "image/jpeg",
                            sizeBytes = 10,
                            downloadUrl = "https://cdn/blob",
                            image = ImageMetadata(width = 800, height = 600, blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
                        ),
                    ),
                ),
            ),
        ),
        caption = null,
    )

    private val cash = { text: String ->
        LinkCard.Cash(
            url = cashLink,
            start = text.indexOf(cashLink),
            end = text.indexOf(cashLink) + cashLink.length,
            entropy = "KNi8pQr1n5hRU65vKJGge3",
            state = LinkCard.Cash.State.Resolved(
                amount = "$5.00",
                claim = LinkCard.Cash.Claim.Claimable,
                token = Token.usdf,
            ),
        )
    }

    private class HeldResolution : LinkCardResolution {
        override val revision = MutableStateFlow(0)
        override fun peek(card: LinkCard): LinkCard = card
        override suspend fun resolve(card: LinkCard): LinkCard = card
    }

    private fun set(
        caption: String,
        card: LinkCard? = null,
        onAction: (ChatAction) -> Unit = {},
    ) {
        // The list's own pass, so the row is what the transcript would draw.
        val row = ChatListItem.ContentBubble(
            messageId = 7,
            contentIndex = 0,
            content = photo.copy(caption = MessageContent.Text(caption)),
            isFromSelf = false,
            timestamp = Instant.fromEpochSeconds(1_000),
            photo = PhotoMessageContext(ChatId(ByteArray(16)), sealed = false, redacted = false, previewing = true),
            linkCard = card,
        ).splitAroundLinkCard().single()
        composeTestRule.setContent {
            DesignSystem {
                CompositionLocalProvider(
                    LocalChatActionHandler provides onAction,
                    LocalLinkCardResolution provides HeldResolution(),
                ) {
                    ContentBubble(item = row, position = BubblePosition.Solo)
                }
            }
        }
    }

    @Test
    fun `a flipcash link among words draws as caption text with no card`() {
        val text = "here you go $cashLink"
        set(text, cash(text))

        composeTestRule.onNodeWithTag(PHOTO_CAPTION_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Tap to claim").assertCountEquals(0)
    }

    private val inviteUrl = "https://app.flipcash.com/chat/06d26e5c-a399-8983-98ea-e59785db34a0"

    private fun invite(text: String) = LinkCard.GroupInvite(
        url = inviteUrl,
        start = text.indexOf(inviteUrl),
        end = text.indexOf(inviteUrl) + inviteUrl.length,
        chatId = ChatId(ByteArray(16)),
        state = LinkCard.GroupInvite.State.Resolved(
            title = "XCTest Bed",
            picture = null,
            memberCount = 3,
            requirement = null,
        ),
    )

    @Test
    fun `a group invite among words draws the caption without the link and the card under it`() {
        val text = "join us $inviteUrl"
        set(text, invite(text))

        composeTestRule.onNodeWithText("join us").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(text, substring = true).assertCountEquals(0)
        composeTestRule.onNodeWithText("XCTest Bed", substring = true).assertIsDisplayed()
        val captionBox = composeTestRule.onNodeWithTag(PHOTO_CAPTION_TAG).getUnclippedBoundsInRoot()
        val card = composeTestRule.onNodeWithText("XCTest Bed", substring = true).getUnclippedBoundsInRoot()
        assertTrue(card.top >= captionBox.bottom)
    }

    @Test
    fun `the claim pill on a link-only caption acts as it does in a text message`() {
        val actions = mutableListOf<ChatAction>()
        val card = cash(cashLink)
        set(cashLink, card, onAction = { actions += it })

        composeTestRule.onNodeWithText("Tap to claim").performClick()

        assertEquals(listOf<ChatAction>(ChatAction.CashLinkOpened(card.entropy, card.url)), actions.toList())
    }

    @Test
    fun `a caption that is only a flipcash link draws the card with no caption row`() {
        set(cashLink, cash(cashLink))

        composeTestRule.onAllNodesWithTag(PHOTO_CAPTION_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText("Tap to claim").assertIsDisplayed()
        val photoBox = composeTestRule.onNodeWithTag(PHOTO_BUBBLE_TAG).getUnclippedBoundsInRoot()
        val claim = composeTestRule.onNodeWithText("Tap to claim").getUnclippedBoundsInRoot()
        assertTrue(claim.top >= photoBox.bottom)
    }

    @Test
    fun `an emoji-only caption is drawn large with no caption bubble`() {
        set("😀😀")

        composeTestRule.onNodeWithTag(JUMBO_EMOJI_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PHOTO_CAPTION_TAG).assertCountEquals(0)
    }

    @Test
    fun `a caption of emoji and words is an ordinary caption`() {
        set("😀 nice")

        composeTestRule.onNodeWithTag(PHOTO_CAPTION_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(JUMBO_EMOJI_TAG).assertCountEquals(0)
    }
}
