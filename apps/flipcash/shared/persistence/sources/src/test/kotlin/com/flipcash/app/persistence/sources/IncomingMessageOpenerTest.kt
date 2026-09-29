package com.flipcash.app.persistence.sources

import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.FakeChatCipher
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.chat.UndecryptableReason
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.time.Instant

class IncomingMessageOpenerTest {

    private val chatId = ChatId(ByteArray(32) { 7 })
    private val self: ID = List(16) { 1 }
    private val peer: ID = List(16) { 2 }
    private val selfKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })
    private val peerKeys = KeyPair(publicKey = ByteArray(32) { 21 }, privateKey = ByteArray(64) { 22 })

    private class Keys(private val own: KeyPair, private val peerPk: ByteArray) : ChatKeySource {
        var peerFails = false
        var fetches = 0
        override fun ownKeyPair() = own
        override suspend fun peerPublicKey(userId: ID): Result<ByteArray> {
            fetches++
            return if (peerFails) Result.failure(IOException("offline")) else Result.success(peerPk)
        }
    }

    private val selfSide = Keys(selfKeys, peerKeys.publicKey)
    private val opener = IncomingMessageOpener(ChatContentCrypto(FakeChatCipher, selfSide))
    private val theirs = ChatContentCrypto(FakeChatCipher, Keys(peerKeys, selfKeys.publicKey))
    private val mine = ChatContentCrypto(FakeChatCipher, Keys(selfKeys, peerKeys.publicKey))

    private suspend fun fromPeer(text: String) =
        theirs.seal(chatId, peerId = self, content = MessageContent.Text(text)).getOrThrow()

    private fun message(content: MessageContent, senderId: ID? = peer, id: Long = 1) = ChatMessage(
        messageId = id,
        senderId = senderId,
        content = listOf(content),
        timestamp = Instant.fromEpochSeconds(1_000),
        unreadSeq = 0,
        isFromSelf = senderId == self,
    )

    private suspend fun open(vararg messages: ChatMessage, peerId: ID? = peer, stored: ChatMessage? = null) =
        opener.open(chatId, selfId = self, peerId = peerId, messages = messages.toList(), stored = { stored })

    @Test
    fun `a message from the peer is stored as its plaintext beside the ciphertext`() = runTest {
        val sealed = fromPeer("hi")

        val opened = open(message(sealed)).single()

        assertEquals(listOf(MessageContent.Text("hi")), opened.content)
        assertEquals(MessageEncryption.Decrypted(sealed), opened.encryption)
    }

    @Test
    fun `the viewer's own message opens against the stored peer`() = runTest {
        val sealed = mine.seal(chatId, peerId = peer, content = MessageContent.Text("mine")).getOrThrow()

        val opened = open(message(sealed, senderId = self)).single()

        assertEquals(listOf(MessageContent.Text("mine")), opened.content)
    }

    @Test
    fun `the viewer's own message waits for the peer when members aren't stored`() = runTest {
        val sealed = mine.seal(chatId, peerId = peer, content = MessageContent.Text("mine")).getOrThrow()

        val opened = open(message(sealed, senderId = self), peerId = null).single()

        assertEquals(MessageEncryption.KeyPending, opened.encryption)
        assertEquals(listOf<MessageContent>(sealed), opened.content)
    }

    @Test
    fun `a failed key fetch leaves the message pending rather than undecryptable`() = runTest {
        val sealed = fromPeer("hi")
        selfSide.peerFails = true

        val opened = open(message(sealed)).single()

        assertEquals(MessageEncryption.KeyPending, opened.encryption)
        assertEquals(listOf<MessageContent>(sealed), opened.content)
    }

    @Test
    fun `tampered ciphertext fails authentication`() = runTest {
        val sealed = fromPeer("hi")
        val tampered = sealed.copy(ciphertext = sealed.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() })

        val opened = open(message(tampered)).single()

        assertEquals(MessageEncryption.Undecryptable(UndecryptableReason.Authentication), opened.encryption)
    }

    @Test
    fun `an unknown scheme is unsupported`() = runTest {
        val opened = open(message(fromPeer("hi").copy(scheme = 2))).single()

        assertEquals(MessageEncryption.Undecryptable(UndecryptableReason.Unsupported), opened.encryption)
    }

    @Test
    fun `a stored copy with the same ciphertext is reused without opening`() = runTest {
        val sealed = fromPeer("hi")
        val stored = message(MessageContent.Text("hi")).copy(encryption = MessageEncryption.Decrypted(sealed))

        val opened = open(message(sealed), stored = stored).single()

        assertEquals(stored.content, opened.content)
        assertEquals(0, selfSide.fetches)
    }

    @Test
    fun `an edit arrives as new ciphertext and is opened again`() = runTest {
        val original = fromPeer("hi")
        val edited = fromPeer("hello")
        val stored = message(MessageContent.Text("hi")).copy(encryption = MessageEncryption.Decrypted(original))

        val opened = open(message(edited), stored = stored).single()

        assertEquals(listOf(MessageContent.Text("hello")), opened.content)
    }

    @Test
    fun `plaintext and confirmed sends pass through`() = runTest {
        val plain = message(MessageContent.Text("plain"))
        val confirmed = message(MessageContent.Text("sent"), senderId = self)
            .copy(encryption = MessageEncryption.Decrypted(fromPeer("x")))

        assertEquals(listOf(plain, confirmed), open(plain, confirmed))
    }

    @Test
    fun `a pending message opens once the key is back`() = runTest {
        val sealed = fromPeer("hi")
        val pending = message(sealed).copy(encryption = MessageEncryption.KeyPending)

        val reopened = opener.reopen(chatId, self, peer, pending)

        assertEquals(listOf(MessageContent.Text("hi")), reopened.content)
        assertEquals(MessageEncryption.Decrypted(sealed), reopened.encryption)
    }
}
