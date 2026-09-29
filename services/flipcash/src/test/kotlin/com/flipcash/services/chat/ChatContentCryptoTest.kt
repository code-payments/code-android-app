package com.flipcash.services.chat

import com.codeinc.flipcash.gen.messaging.v1.Model as MessagingModel
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.MessageContent
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
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
    fun `media is not sealed`() = runTest {
        val result = mine.seal(chatId, peer, MessageContent.Media(items = emptyList(), caption = null))

        assertTrue(result.isFailure)
    }

    @Test
    fun `an unknown scheme asks for an update without fetching keys`() = runTest {
        val sealed = sealFromPeer(MessageContent.Text("hi")).copy(scheme = 2)

        assertEquals(OpenedContent.Undecryptable(UndecryptableReason.Unsupported), open(sealed))
        assertEquals(0, selfSide.fetches)
    }

    @Test
    fun `a plaintext type outside text and reply asks for an update`() = runTest {
        val media = MessagingModel.Content.newBuilder()
            .setMedia(MessagingModel.MediaContent.getDefaultInstance())
            .build()
        val sealed = sealRaw(media.toByteArray())

        assertEquals(OpenedContent.Undecryptable(UndecryptableReason.Unsupported), open(sealed))
    }

    @Test
    fun `a reply to media asks for an update`() = runTest {
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
