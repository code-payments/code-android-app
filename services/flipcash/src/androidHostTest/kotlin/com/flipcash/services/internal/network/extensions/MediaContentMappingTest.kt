package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.blob.v1.Model as BlobModel
import com.codeinc.flipcash.gen.blob.v1.Model.BlobMetadata.KindCase
import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The photo `Content` plaintexts in `libs/encryption/chat-cipher/.../chat_cipher.json`, shared
 * with iOS, must survive proto -> domain -> proto byte for byte, since that is what both clients
 * seal and open.
 */
class MediaContentMappingTest {

    private fun rendition(size: Long, width: Int, height: Int, mime: String, blurhash: String) =
        BlobModel.Rendition.newBuilder()
            .setRole(BlobModel.Rendition.Role.ORIGINAL)
            .setBlobId(BlobModel.BlobId.newBuilder().setValue(ByteString.copyFrom(ByteArray(16) { (0xA0 + it).toByte() })))
            .setBlob(
                BlobModel.BlobMetadata.newBuilder()
                    .setMimeType(mime)
                    .setSizeBytes(size)
                    .setImage(BlobModel.ImageMetadata.newBuilder().setWidth(width).setHeight(height).setBlurhash(blurhash))
            )

    private fun media(caption: String?, rendition: BlobModel.Rendition.Builder) =
        MessagingModel.MediaContent.newBuilder()
            .addItems(BlobModel.Media.newBuilder().addRenditions(rendition))
            .apply { caption?.let { setCaption(MessagingModel.TextContent.newBuilder().setText(it)) } }

    /**
     * The values of the `chat_cipher.json` photo plaintexts (reply-to-media, media-with-caption,
     * media-no-caption), built with the pinned protos. The fixture's own bytes nest one level
     * deeper than `blob.v1.Media` / `messaging.v1.MediaContent` allow, so they cannot be decoded
     * into these messages and are not used verbatim.
     */
    private val vectors = mapOf(
        "media-with-caption" to MessagingModel.Content.newBuilder()
            .setMedia(media("a caption", rendition(123456, 1024, 768, "image/jpeg", "LEHV6nWB2yk8pyo0adR*.7kCMdnj"))).build(),
        "media-no-caption" to MessagingModel.Content.newBuilder()
            .setMedia(media(null, rendition(99, 1, 1, "image/png", ""))).build(),
        "reply-to-media" to MessagingModel.Content.newBuilder()
            .setReply(
                MessagingModel.ReplyContent.newBuilder()
                    .setRepliedMessageId(MessagingModel.MessageId.newBuilder().setValue(7))
                    .addContent(
                        MessagingModel.Content.newBuilder()
                            .setMedia(media("look", rendition(128, 640, 480, "image/jpeg", "LEHV6nWB2yk8pyo0adR*.7kCMdnj")))
                    )
            ).build(),
    )

    @Test
    fun `photo plaintexts round-trip through the domain unchanged`() {
        for ((name, content) in vectors) {
            val back = content.toMessageContent().asContent().toByteArray()

            assertContentEquals(content.toByteArray(), back, name)
        }
    }

    @Test
    fun `a photo maps to the domain with metadata, caption and no url`() {
        val media = vectors.getValue("media-with-caption").toMessageContent() as MessageContent.Media

        val rendition = media.items.single().renditions.single()
        assertEquals(MediaItemRendition.Role.ORIGINAL, rendition.role)
        assertEquals(16, rendition.blobId.bytes.size)
        assertEquals("image/jpeg", rendition.blob?.mimeType)
        assertEquals(123456L, rendition.blob?.sizeBytes)
        assertEquals("", rendition.blob?.downloadUrl)
        assertEquals(MessageContent.Text("a caption"), media.caption)
    }

    @Test
    fun `a photo with no caption has none, even when the caption is present and empty`() {
        val parsed = vectors.getValue("media-no-caption")
        val empty = parsed.toBuilder()
            .setMedia(parsed.media.toBuilder().setCaption(MessagingModel.TextContent.newBuilder().setText("")))
            .build()

        assertEquals(null, (empty.toMessageContent() as MessageContent.Media).caption)
    }

    @Test
    fun `a sealed photo emits one original rendition with metadata and no download url`() {
        val media = MessageContent.Media(
            items = listOf(
                MediaItem(
                    listOf(
                        MediaItemRendition(
                            role = MediaItemRendition.Role.ORIGINAL,
                            blobId = BlobId(ByteArray(16) { it.toByte() }),
                            blob = BlobMetadata(
                                mimeType = "image/jpeg",
                                sizeBytes = 4242,
                                downloadUrl = "",
                                image = ImageMetadata(width = 800, height = 600, blurhash = "abc"),
                            ),
                        )
                    )
                )
            ),
            caption = null,
        )

        val proto = media.asContent().media
        val rendition = proto.getItems(0).getRenditions(0)

        assertEquals(1, proto.itemsCount)
        assertEquals(1, proto.getItems(0).renditionsCount)
        assertEquals(com.codeinc.flipcash.gen.blob.v1.Model.Rendition.Role.ORIGINAL, rendition.role)
        assertEquals(16, rendition.blobId.value.size())
        assertEquals("image/jpeg", rendition.blob.mimeType)
        assertEquals(4242L, rendition.blob.sizeBytes)
        assertEquals(KindCase.IMAGE, rendition.blob.kindCase)
        assertEquals(800, rendition.blob.image.width)
        assertEquals(600, rendition.blob.image.height)
        assertEquals("abc", rendition.blob.image.blurhash)
        assertFalse(rendition.blob.hasDownloadUrl())
        assertFalse(proto.hasCaption())
    }

    @Test
    fun `a rendition with no metadata still emits only its id`() {
        val media = MessageContent.Media(
            listOf(MediaItem(listOf(MediaItemRendition(MediaItemRendition.Role.ORIGINAL, BlobId(ByteArray(16)), null)))),
            null,
        )

        assertFalse(media.asContent().media.getItems(0).getRenditions(0).hasBlob())
        assertTrue(media.asContent().media.getItems(0).getRenditions(0).hasBlobId())
    }
}
