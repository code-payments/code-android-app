package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.IncomingMessageOpener
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.FakeChatCipher
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** What a DM push shows: the opened plaintext, or `null` so the server's body stays. */
class MessagingPushDecryptionTest {

    private val chatId = ChatId(ByteArray(32) { 7 })
    private val self: ID = List(16) { 1 }
    private val peer: ID = List(16) { 2 }
    private val selfKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })
    private val peerKeys = KeyPair(publicKey = ByteArray(32) { 21 }, privateKey = ByteArray(64) { 22 })

    private class Keys(private val own: KeyPair, private val peerPk: ByteArray) : ChatKeySource {
        var peerFails = false
        override fun ownKeyPair() = own
        override suspend fun peerPublicKey(userId: ID): Result<ByteArray> =
            if (peerFails) Result.failure(IOException("offline")) else Result.success(peerPk)
    }

    private val selfSide = Keys(selfKeys, peerKeys.publicKey)
    private val theirs = ChatContentCrypto(FakeChatCipher, Keys(peerKeys, selfKeys.publicKey))
    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)
    private val messagingController = mockk<ChatMessagingController>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true) {
        every { accountId } returns self
    }

    private val delegate = MessagingDelegate(
        chatController = mockk(relaxed = true),
        messagingController = messagingController,
        metadataDataSource = mockk(relaxed = true),
        messageDataSource = messageDataSource,
        memberDataSource = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        userManager = userManager,
        stateHolder = mockk(relaxed = true),
        analytics = mockk(relaxed = true),
        senderResolver = mockk(relaxed = true),
        incoming = IncomingMessageOpener(ChatContentCrypto(FakeChatCipher, selfSide)),
    )

    private suspend fun sealedFromPeer(content: MessageContent) =
        theirs.seal(chatId, peerId = self, content = content).getOrThrow()

    private fun message(content: MessageContent, id: Long = 5) = ChatMessage(
        messageId = id,
        senderId = peer,
        content = listOf(content),
        timestamp = Instant.fromEpochSeconds(1_000),
        unreadSeq = 0,
    )

    @Test
    fun `an inlined encrypted message shows its plaintext`() = runTest {
        val pushed = message(sealedFromPeer(MessageContent.Text("see you at 8")))

        assertEquals("see you at 8", delegate.openPushedMessage(chatId, pushed, messageId = null))
    }

    @Test
    fun `a reply shows the text it carries`() = runTest {
        val reply = MessageContent.Reply(repliedMessageId = 2, content = listOf(MessageContent.Text("yes")))
        val pushed = message(sealedFromPeer(reply))

        assertEquals("yes", delegate.openPushedMessage(chatId, pushed, messageId = null))
    }

    @Test
    fun `an id-only push fetches the message when it isn't stored`() = runTest {
        val fetched = message(sealedFromPeer(MessageContent.Text("long one")), id = 9)
        coEvery { messageDataSource.getMessage(chatId, 9) } returns null
        coEvery { messagingController.getMessage(chatId, 9, any()) } returns Result.success(fetched)

        assertEquals("long one", delegate.openPushedMessage(chatId, message = null, messageId = 9))
    }

    @Test
    fun `an id-only push reads an already-opened stored copy without fetching`() = runTest {
        val sealed = sealedFromPeer(MessageContent.Text("stored"))
        val stored = message(MessageContent.Text("stored"), id = 9)
            .copy(encryption = MessageEncryption.Decrypted(sealed))
        coEvery { messageDataSource.getMessage(chatId, 9) } returns stored

        assertEquals("stored", delegate.openPushedMessage(chatId, message = null, messageId = 9))
        coVerify(exactly = 0) { messagingController.getMessage(any(), any(), any()) }
    }

    @Test
    fun `a failed fetch keeps the server's body`() = runTest {
        coEvery { messageDataSource.getMessage(chatId, 9) } returns null
        coEvery { messagingController.getMessage(chatId, 9, any()) } returns Result.failure(IOException("offline"))

        assertNull(delegate.openPushedMessage(chatId, message = null, messageId = 9))
    }

    @Test
    fun `a plaintext message keeps the server's body`() = runTest {
        assertNull(delegate.openPushedMessage(chatId, message(MessageContent.Text("hi")), messageId = null))
    }

    @Test
    fun `a message that fails to open keeps the server's body`() = runTest {
        val sealed = sealedFromPeer(MessageContent.Text("hi"))
        val tampered = sealed.copy(ciphertext = sealed.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() })

        assertNull(delegate.openPushedMessage(chatId, message(tampered), messageId = null))
    }

    @Test
    fun `a key fetch failure keeps the server's body`() = runTest {
        val pushed = message(sealedFromPeer(MessageContent.Text("hi")))
        selfSide.peerFails = true

        assertNull(delegate.openPushedMessage(chatId, pushed, messageId = null))
    }

    @Test
    fun `nothing to open without a message or its id`() = runTest {
        assertNull(delegate.openPushedMessage(chatId, message = null, messageId = null))
    }
}
