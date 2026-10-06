package com.flipcash.services.chat

import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.internal.network.extensions.asContent
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.MessageContent
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatContentCryptoTest {

    private val chatId = ChatId(ByteArray(32) { 7 })
    private val self: ID = List(16) { 1 }
    private val peer: ID = List(16) { 2 }
    private val selfKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })
    private val peerKeys = KeyPair(publicKey = ByteArray(32) { 21 }, privateKey = ByteArray(64) { 22 })

    private class Keys(own: KeyPair, private val peerPk: ByteArray) : ChatKeySource {
        var own: KeyPair? = own
        var peerFails: Throwable? = null
        var fetches = 0
        override fun ownKeyPair() = own
        override suspend fun peerPublicKey(userId: ID): Result<ByteArray> {
            fetches++
            return peerFails?.let { Result.failure(it) } ?: Result.success(peerPk)
        }
    }

    private val selfSide = Keys(selfKeys, peerKeys.publicKey)
    private val peerSide = Keys(peerKeys, selfKeys.publicKey)
    private val mine = ChatContentCrypto(FakeChatCipher, selfSide)
    private val theirs = ChatContentCrypto(FakeChatCipher, peerSide)

    private suspend fun sealFromPeer(content: MessageContent) =
        theirs.seal(chatId, peerId = self, content = content).getOrThrow()

    private suspend fun open(encrypted: MessageContent.Encrypted, senderId: ID? = peer) =
        mine.open(chatId, selfId = self, peerId = peer, senderId = senderId, encrypted = encrypted)

    @Test
    fun `text from the peer opens`() = runTest {
        val sealed = sealFromPeer(MessageContent.Text("hi"))

        assertEquals(OpenedContent.Plaintext(MessageContent.Text("hi")), open(sealed))
    }

    @Test
    fun `a reply to text opens with its quote`() = runTest {
        val reply = MessageContent.Reply(repliedMessageId = 4, content = listOf(MessageContent.Text("yes")))

        assertEquals(OpenedContent.Plaintext(reply), open(sealFromPeer(reply)))
    }

    @Test
    fun `the viewer's own message opens`() = runTest {
        val sealed = mine.seal(chatId, peerId = peer, content = MessageContent.Text("mine")).getOrThrow()

        assertEquals(OpenedContent.Plaintext(MessageContent.Text("mine")), open(sealed, senderId = self))
    }

    @Test
    fun `media with no items is not sealed`() = runTest {
        val result = mine.seal(chatId, peer, MessageContent.Media(items = emptyList(), caption = null))

        assertTrue(result.isFailure)
    }

    private val blobId = ByteArray(16) { (it + 0xA0).toByte() }

    private fun photo(
        role: MediaItemRendition.Role = MediaItemRendition.Role.ORIGINAL,
        id: ByteArray? = blobId,
        blob: BlobMetadata? = BlobMetadata(
            mimeType = "image/jpeg",
            sizeBytes = 100,
            downloadUrl = "",
            image = ImageMetadata(width = 640, height = 480, blurhash = "LEHV6nWB2yk8pyo0adR*.7kCMdnj"),
        ),
    ) = MessageContent.Media(
        items = listOf(MediaItem(listOf(MediaItemRendition(role, BlobId(id ?: ByteArray(0)), blob)))),
        caption = MessageContent.Text("look"),
    )

    private fun BlobMetadata.image(i: ImageMetadata?) = copy(image = i)

    @Test
    fun `a photo and a reply to a photo are sealed and open for the peer`() = runTest {
        val media = photo()
        assertEquals(OpenedContent.Plaintext(media), open(sealFromPeer(media)))

        val reply = MessageContent.Reply(repliedMessageId = 9, content = listOf(media))
        assertEquals(OpenedContent.Plaintext(reply), open(sealFromPeer(reply)))
    }

    @Test
    fun `a download url never goes on the wire for a sealed photo`() = runTest {
        val withUrl = photo(blob = photo().items[0].renditions[0].blob!!.copy(downloadUrl = "https://x/y", expiresAtMillis = 5))

        assertEquals(OpenedContent.Plaintext(photo()), open(sealFromPeer(withUrl)))
    }

    @Test
    fun `a reply that mixes media with text is not sealed`() = runTest {
        val reply = MessageContent.Reply(4, listOf(photo(), MessageContent.Text("x")))

        assertTrue(mine.seal(chatId, peer, reply).isFailure)
    }

    private fun contractCases(): Map<String, MessageContent.Media> {
        val ok = photo().items[0].renditions[0].blob!!
        val image = ok.image!!
        fun withBlob(b: BlobMetadata?) = photo(blob = b)
        return mapOf(
            "wrong role" to photo(role = MediaItemRendition.Role.DISPLAY),
            "short blob id" to photo(id = ByteArray(15)),
            "long blob id" to photo(id = ByteArray(17)),
            "no blob" to withBlob(null),
            "empty mime" to withBlob(ok.copy(mimeType = "")),
            "non-image mime" to withBlob(ok.copy(mimeType = "video/mp4")),
            "long mime" to withBlob(ok.copy(mimeType = "image/" + "a".repeat(250))),
            "zero size" to withBlob(ok.copy(sizeBytes = 0)),
            "no image kind" to withBlob(ok.image(null)),
            "zero width" to withBlob(ok.image(image.copy(width = 0))),
            "zero height" to withBlob(ok.image(image.copy(height = 0))),
            "long blurhash" to withBlob(ok.image(image.copy(blurhash = "a".repeat(65)))),
            "two items" to photo().let { it.copy(items = it.items + it.items) },
            "two renditions" to photo().let {
                it.copy(items = listOf(MediaItem(it.items[0].renditions + it.items[0].renditions)))
            },
            "no renditions" to MessageContent.Media(listOf(MediaItem(emptyList())), null),
        )
    }

    @Test
    fun `a photo outside the contract is neither sealed nor opened`() = runTest {
        for ((name, media) in contractCases()) {
            assertTrue(mine.seal(chatId, peer, media).isFailure, "seal: $name")
            assertTrue(mine.seal(chatId, peer, MessageContent.Reply(1, listOf(media))).isFailure, "seal reply: $name")
            // Seal the raw bytes directly, as a peer running other code could.
            val raw = media.asContent().toByteArray()
            assertEquals(
                OpenedContent.Undecryptable(UndecryptableReason.Unsupported),
                open(sealRaw(raw)),
                "open: $name",
            )
        }
    }

    @Test
    fun `mime prefix is case-insensitive and an empty blurhash and caption are absent`() = runTest {
        val ok = photo().items[0].renditions[0].blob!!
        val media = photo(blob = ok.copy(mimeType = "IMAGE/PNG").image(ok.image!!.copy(blurhash = "")))
            .copy(caption = null)

        assertEquals(OpenedContent.Plaintext(media), open(sealFromPeer(media)))

        val proto = media.asContent()
        val emptyCaption = proto.toBuilder()
            .setMedia(proto.media.toBuilder().setCaption(MessagingModel.TextContent.newBuilder().setText("")))
            .build()
        assertEquals(OpenedContent.Plaintext(media), open(sealRaw(emptyCaption.toByteArray())))
    }

    @Test
    fun `blob sealed by the viewer opens for the peer and for the viewer's other device`() = runTest {
        val sealed = mine.sealBlob(chatId, peer, blobId, byteArrayOf(1, 2, 3)).getOrThrow()

        val asPeer = theirs.openBlob(chatId, selfId = peer, peerId = self, senderId = self, blobId = blobId, expectedSize = 3, sealed = sealed)
        assertContentEquals(byteArrayOf(1, 2, 3), assertIs<OpenedBlob.Plaintext>(asPeer).bytes)

        val otherDevice = ChatContentCrypto(FakeChatCipher, Keys(selfKeys, peerKeys.publicKey))
            .openBlob(chatId, selfId = self, peerId = peer, senderId = self, blobId = blobId, expectedSize = 3, sealed = sealed)
        assertContentEquals(byteArrayOf(1, 2, 3), assertIs<OpenedBlob.Plaintext>(otherDevice).bytes)
    }

    @Test
    fun `blob key roles follow the sender`() = runTest {
        val fromPeer = theirs.sealBlob(chatId, self, blobId, byteArrayOf(9)).getOrThrow()

        // Right: the peer sent it.
        assertIs<OpenedBlob.Plaintext>(
            mine.openBlob(chatId, self, peer, senderId = peer, blobId = blobId, expectedSize = 1, sealed = fromPeer),
        )
        // Wrong: claiming the viewer sent it swaps the roles.
        assertEquals(
            OpenedBlob.Failed(BlobOpenFailure.Authentication),
            mine.openBlob(chatId, self, peer, senderId = self, blobId = blobId, expectedSize = 1, sealed = fromPeer),
        )
        // A sender who is neither member can't have sealed it.
        assertEquals(
            OpenedBlob.Failed(BlobOpenFailure.Authentication),
            mine.openBlob(chatId, self, peer, senderId = null, blobId = blobId, expectedSize = 1, sealed = fromPeer),
        )
    }

    @Test
    fun `blob failures are typed`() = runTest {
        val sealed = theirs.sealBlob(chatId, self, blobId, byteArrayOf(1, 2, 3)).getOrThrow()

        assertEquals(
            OpenedBlob.Failed(BlobOpenFailure.Length),
            mine.openBlob(chatId, self, peer, peer, blobId, expectedSize = 4, sealed = sealed),
        )
        assertEquals(
            OpenedBlob.Failed(BlobOpenFailure.Authentication),
            mine.openBlob(chatId, self, peer, peer, ByteArray(16), expectedSize = 3, sealed = sealed),
        )
        val cold = Keys(selfKeys, peerKeys.publicKey).apply { peerFails = IOException("offline") }
        assertEquals(
            OpenedBlob.Failed(BlobOpenFailure.KeyPending),
            ChatContentCrypto(FakeChatCipher, cold).openBlob(chatId, self, peer, peer, blobId, expectedSize = 3, sealed = sealed),
        )
    }

    @Test
    fun `blob sealing guard matches the chat`() {
        val other = ChatId(ByteArray(32) { 8 })

        assertTrue(checkBlobSealing(sealedFor = chatId, sendChatId = chatId, chatSeals = true).isSuccess)
        assertTrue(checkBlobSealing(sealedFor = null, sendChatId = chatId, chatSeals = false).isSuccess)
        assertTrue(checkBlobSealing(sealedFor = null, sendChatId = chatId, chatSeals = true).isFailure)
        assertTrue(checkBlobSealing(sealedFor = chatId, sendChatId = chatId, chatSeals = false).isFailure)
        assertTrue(checkBlobSealing(sealedFor = other, sendChatId = chatId, chatSeals = true).isFailure)
    }

    @Test
    fun `an unknown scheme asks for an update without fetching keys`() = runTest {
        val sealed = sealFromPeer(MessageContent.Text("hi")).copy(scheme = 2)

        assertEquals(OpenedContent.Undecryptable(UndecryptableReason.Unsupported), open(sealed))
        assertEquals(0, selfSide.fetches)
    }

    @Test
    fun `a plaintext type outside text, media and reply asks for an update`() = runTest {
        val media = MessagingModel.Content.newBuilder()
            .setMedia(MessagingModel.MediaContent.getDefaultInstance())
            .build()
        val sealed = sealRaw(media.toByteArray())

        assertEquals(OpenedContent.Undecryptable(UndecryptableReason.Unsupported), open(sealed))
    }

    @Test
    fun `a reply to empty media asks for an update`() = runTest {
        val replyToMedia = MessagingModel.Content.newBuilder()
            .setReply(
                MessagingModel.ReplyContent.newBuilder()
                    .setRepliedMessageId(MessagingModel.MessageId.newBuilder().setValue(1))
                    .addContent(
                        MessagingModel.Content.newBuilder()
                            .setMedia(MessagingModel.MediaContent.getDefaultInstance())
                    )
            )
            .build()

        assertEquals(
            OpenedContent.Undecryptable(UndecryptableReason.Unsupported),
            open(sealRaw(replyToMedia.toByteArray())),
        )
    }

    @Test
    fun `a tampered ciphertext fails authentication`() = runTest {
        val sealed = sealFromPeer(MessageContent.Text("hi"))
        val tampered = sealed.copy(ciphertext = sealed.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() })

        assertEquals(OpenedContent.Undecryptable(UndecryptableReason.Authentication), open(tampered))
    }

    @Test
    fun `a message attributed to the wrong sender fails authentication`() = runTest {
        val sealed = sealFromPeer(MessageContent.Text("hi"))

        assertEquals(
            OpenedContent.Undecryptable(UndecryptableReason.Authentication),
            open(sealed, senderId = self),
        )
    }

    @Test
    fun `a failed key fetch is pending, not undecryptable`() = runTest {
        val sealed = sealFromPeer(MessageContent.Text("hi"))
        selfSide.peerFails = IOException("offline")

        assertEquals(OpenedContent.KeyPending, open(sealed))

        selfSide.peerFails = null
        assertIs<OpenedContent.Plaintext>(open(sealed))
    }

    @Test
    fun `no account key pair is pending`() = runTest {
        val sealed = sealFromPeer(MessageContent.Text("hi"))
        selfSide.own = null

        assertEquals(OpenedContent.KeyPending, open(sealed))
    }

    @Test
    fun `the chat key is fetched once per chat`() = runTest {
        repeat(3) { open(sealFromPeer(MessageContent.Text("$it"))) }

        assertEquals(1, selfSide.fetches)
    }

    private suspend fun sealRaw(plaintext: ByteArray): MessageContent.Encrypted {
        val chatKey = FakeChatCipher.chatKey(peerKeys, selfKeys.publicKey, chatId.bytes)
        val payload = FakeChatCipher.encrypt(plaintext, chatKey, peerKeys.publicKey, selfKeys.publicKey, chatId.bytes)
        return MessageContent.Encrypted(ChatContentCrypto.SCHEME_X25519_XCHACHA20POLY1305, payload.nonce, payload.ciphertext)
    }
}
