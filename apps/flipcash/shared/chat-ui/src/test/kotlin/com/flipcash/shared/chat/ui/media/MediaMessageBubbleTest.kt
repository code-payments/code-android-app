package com.flipcash.shared.chat.ui.media

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    private fun item(previewing: Boolean) = ChatListItem.ContentBubble(
        messageId = 7,
        contentIndex = 0,
        content = photo,
        isFromSelf = false,
        timestamp = Instant.fromEpochSeconds(1_000),
        photo = PhotoMessageContext(chatId, sealed = false, redacted = false, previewing = previewing),
    )

    @Test
    fun `a media message renders a photo bubble with its caption`() {
        composeTestRule.setContent {
            DesignSystem { ContentBubble(item = item(previewing = true), position = BubblePosition.Solo) }
        }

        composeTestRule.onNodeWithTag(PHOTO_BUBBLE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("sunset").assertIsDisplayed()
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
}
