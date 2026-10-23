package com.flipcash.shared.chat.ui.media

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTouchInput
import com.flipcash.services.chat.BlobOpenFailure
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.media.ChatPhotoUnavailable
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.LinkCard
import com.flipcash.shared.chat.ui.WEB_LINK_CARD_TAG
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import com.flipcash.shared.chat.models.PhotoMessageContext
import com.flipcash.shared.chat.ui.BubblePosition
import com.flipcash.shared.chat.ui.ContentBubble
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class MediaMessageBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val chatId = ChatId(ByteArray(16) { it.toByte() })

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
        caption = MessageContent.Text("sunset"),
    )

    private fun item(previewing: Boolean, quote: ChatQuote? = null) = ChatListItem.ContentBubble(
        messageId = 7,
        contentIndex = 0,
        content = if (quote != null) MessageContent.Reply(repliedMessageId = quote.messageId, content = listOf(photo)) else photo,
        isFromSelf = false,
        timestamp = Instant.fromEpochSeconds(1_000),
        photo = PhotoMessageContext(chatId, sealed = false, redacted = false, previewing = previewing),
        quote = quote,
    )

    @Test
    fun `a media message renders a photo bubble with its caption`() {
        composeTestRule.setContent {
            DesignSystem { ContentBubble(item = item(previewing = true), position = BubblePosition.Solo) }
        }

        composeTestRule.onNodeWithTag(PHOTO_BUBBLE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("sunset").assertIsDisplayed()
    }

    private fun cardItem(state: LinkCard.Web.State) = item(previewing = true).copy(
        // Words around the link, so the caption keeps its row; a caption that is only the link is
        // the card alone (see MediaCaptionCardTest).
        content = photo.copy(caption = MessageContent.Text("see https://www.example.com/a")),
        linkCard = LinkCard.Web(url = "https://www.example.com/a", start = 4, end = 29, state = state),
    )

    @Test
    fun `a captioned photo draws its resolved link card under the caption`() {
        composeTestRule.setContent {
            DesignSystem {
                ContentBubble(
                    item = cardItem(
                        LinkCard.Web.State.Resolved(
                            title = "The title",
                            description = null,
                            imageUrl = null,
                            host = "example.com",
                        ),
                    ),
                    position = BubblePosition.Solo,
                )
            }
        }

        composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("The title").assertIsDisplayed()
        val caption = composeTestRule.onNodeWithTag(PHOTO_CAPTION_TAG).getUnclippedBoundsInRoot()
        val card = composeTestRule.onNodeWithTag(WEB_LINK_CARD_TAG).getUnclippedBoundsInRoot()
        assertTrue(card.top >= caption.bottom)
    }

    @Test
    fun `a captioned photo whose card has no preview draws no card`() {
        composeTestRule.setContent {
            DesignSystem {
                ContentBubble(item = cardItem(LinkCard.Web.State.Loading), position = BubblePosition.Solo)
            }
        }

        composeTestRule.onAllNodesWithTag(WEB_LINK_CARD_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(PHOTO_CAPTION_TAG).assertIsDisplayed()
    }

    @Test
    fun `double tapping a photo presents the reaction strip`() {
        var doubleTaps = 0
        composeTestRule.setContent {
            DesignSystem {
                ContentBubble(
                    item = item(previewing = false),
                    position = BubblePosition.Solo,
                    onLongClick = {},
                    onDoubleClick = { doubleTaps++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()

        assertEquals(1, doubleTaps)
    }

    @Test
    fun `double tapping a photo with nothing else to do still presents the reaction strip`() {
        var doubleTaps = 0
        composeTestRule.setContent {
            DesignSystem {
                ContentBubble(
                    item = item(previewing = false),
                    position = BubblePosition.Solo,
                    onDoubleClick = { doubleTaps++ },
                )
            }
        }

        composeTestRule.onNodeWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).performTouchInput { doubleClick() }
        composeTestRule.waitForIdle()

        assertEquals(1, doubleTaps)
    }

    @Test
    fun `a photo only opens when it is showing, delivered and loaded`() {
        assertTrue(canOpenPhoto(blurhashOnly = false, failed = false, unavailable = false))
        assertFalse(canOpenPhoto(blurhashOnly = true, failed = false, unavailable = false))
        assertFalse(canOpenPhoto(blurhashOnly = false, failed = true, unavailable = false))
        assertFalse(canOpenPhoto(blurhashOnly = false, failed = false, unavailable = true))
    }

    @Test
    fun `a key that has not arrived leaves the blurhash up instead of reporting unavailable`() {
        assertFalse(isPhotoUnavailable(ChatPhotoUnavailable.Open(BlobOpenFailure.KeyPending)))
        assertTrue(isPhotoUnavailable(ChatPhotoUnavailable.Redacted()))
        assertTrue(isPhotoUnavailable(IllegalStateException("decode")))
        assertEquals(true, isPhotoUnavailable(RuntimeException(ChatPhotoUnavailable.NotFound())))
    }

    @Test
    fun ownSealedPhotoKeepsItsSender() {
        // A sealed photo picks its key roles from the sender. Dropping the own id left the photo
        // with no sender, so it never opened and drew as "can't be displayed".
        val self = List(32) { 7.toByte() }
        val message = ChatMessage(
            messageId = 1,
            senderId = self,
            content = listOf(photo),
            timestamp = Instant.fromEpochMilliseconds(0),
            unreadSeq = 0,
            isFromSelf = true,
            encryption = MessageEncryption.Decrypted(
                MessageContent.Encrypted(scheme = 1, nonce = ByteArray(24), ciphertext = ByteArray(8)),
            ),
        )

        val context = message.photoContext(chatId, previewing = false)!!

        assertEquals(self, context.senderId)
        assertTrue(context.sealed)
    }

    @Test
    fun messageWithoutPhotoHasNoPhotoContext() {
        val message = ChatMessage(
            messageId = 1,
            senderId = null,
            content = listOf(MessageContent.Text("hi")),
            timestamp = Instant.fromEpochMilliseconds(0),
            unreadSeq = 0,
        )

        assertEquals(null, message.photoContext(chatId, previewing = false))
    }

    @Test
    fun `a long quoted message sits inside the photo it replies with`() {
        val quote = ChatQuote(
            messageId = 3,
            authorName = "Someone with a fairly long display name",
            snippet = ChatQuoteSnippet.Text("word ".repeat(80)),
            accent = null,
            nameAccent = null,
        )
        composeTestRule.setContent {
            DesignSystem { ContentBubble(item = item(previewing = true, quote = quote), position = BubblePosition.Solo) }
        }

        val photo = composeTestRule.onNodeWithTag(PHOTO_BUBBLE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val chip = composeTestRule.onNodeWithTag(PHOTO_QUOTE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue(chip.left >= photo.left && chip.right <= photo.right, "chip $chip spills past photo $photo horizontally")
        assertTrue(chip.top >= photo.top && chip.bottom <= photo.bottom, "chip $chip sits outside photo $photo vertically")
        assertTrue(chip.right - chip.left < photo.right - photo.left, "chip $chip fills the whole photo $photo")
    }

    private fun sized(role: MediaItemRendition.Role, longestSide: Int, id: Byte) = MediaItemRendition(
        role = role,
        blobId = BlobId(ByteArray(32) { id }),
        blob = BlobMetadata(
            mimeType = "image/jpeg",
            sizeBytes = longestSide.toLong(),
            downloadUrl = "https://cdn/$longestSide",
            image = ImageMetadata(width = longestSide, height = longestSide * 3 / 4, blurhash = "abc"),
        ),
    )

    @Test
    fun `a bubble loads the smallest derived rendition that fills it, not the original`() {
        val item = MediaItem(
            listOf(
                sized(MediaItemRendition.Role.ORIGINAL, 4000, 1),
                sized(MediaItemRendition.Role.DISPLAY, 1600, 2),
                sized(MediaItemRendition.Role.DISPLAY, 800, 3),
                sized(MediaItemRendition.Role.THUMBNAIL, 320, 4),
            ),
        )
        assertEquals(800, bubbleRendition(item, bubblePx = 700)?.blob?.image?.width)
        assertEquals(1600, bubbleRendition(item, bubblePx = 900)?.blob?.image?.width)
    }

    @Test
    fun `a bubble falls back to the original when the server derived no renditions`() {
        val item = MediaItem(listOf(sized(MediaItemRendition.Role.ORIGINAL, 4000, 1)))
        assertEquals(MediaItemRendition.Role.ORIGINAL, bubbleRendition(item, bubblePx = 700)?.role)
    }
}
