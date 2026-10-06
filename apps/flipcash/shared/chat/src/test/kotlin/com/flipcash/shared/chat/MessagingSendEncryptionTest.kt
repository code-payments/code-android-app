package com.flipcash.shared.chat

import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.PendingMessage
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.chat.FakeChatCipher
import com.flipcash.services.chat.MessageEncryption
import com.flipcash.services.chat.OpenedContent
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.GetChatError
import com.flipcash.services.models.SendMessageError
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.BlobMetadata
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ImageMetadata
import com.flipcash.services.models.chat.MediaItem
import com.flipcash.services.models.chat.MediaItemRendition
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.DmOutgoingEncryption
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.getcode.chatcipher.ChatEncryptionPolicy
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * The send side of end-to-end encrypted DMs: what goes on the wire, what is stored, and what
 * happens when sealing can't be done.
 */
class MessagingSendEncryptionTest {

    private val chatId = ChatId(ByteArray(32) { 3 })
    private val selfId: ID = List(16) { 1 }
    private val peerId: ID = List(16) { 2 }
    private val flipcashId: ID = ChatEncryptionPolicy.FLIPCASH_USER_ID.toList()
    private val clientMessageId = ClientMessageId(ByteArray(16) { 9 })

    private val selfKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })
    private val peerKeys = KeyPair(publicKey = ByteArray(32) { 21 }, privateKey = ByteArray(64) { 22 })

    private class Keys(private val own: KeyPair, private val peerPk: ByteArray) : ChatKeySource {
        var peerFails: Throwable? = null
        override fun ownKeyPair() = own
        override suspend fun peerPublicKey(userId: ID) =
            peerFails?.let { Result.failure<ByteArray>(it) } ?: Result.success(peerPk)
    }

    private val keys = Keys(selfKeys, peerKeys.publicKey)
    private val peerCrypto = ChatContentCrypto(FakeChatCipher, Keys(peerKeys, selfKeys.publicKey))

    private val controller = mockk<ChatMessagingController>(relaxed = true)
    private val chatController = mockk<ChatController>(relaxed = true)
    private val messages = mockk<ChatMessageDataSource>(relaxed = true)
    private val metadata = mockk<ChatMetadataDataSource>(relaxed = true)
    private val members = mockk<ChatMemberDataSource>(relaxed = true)
    private val userManager = mockk<UserManager>(relaxed = true).also {
        every { it.accountId } returns selfId
    }

    private fun chat(type: ChatType = ChatType.CONTACT_DM, useE2ee: Boolean = true, peer: ID = peerId) =
        ChatMetadata(
            chatId = chatId,
            type = type,
            members = listOf(member(selfId), member(peer)),
            lastMessage = null,
            lastActivity = Instant.fromEpochSeconds(0),
            useE2ee = useE2ee,
        )

    private fun member(id: ID) = ChatMember(userId = id, userProfile = UserProfile.Empty, pointers = emptyList())

    private fun storedChat(chat: ChatMetadata?) {
        val entity = chat?.let { mockk<ChatMetadataEntity>() }
        every { metadata.observeById(chatId) } returns flowOf(entity)
        coEvery { members.getMembersForChat(chatId) } returns chat?.members.orEmpty()
        if (chat != null) every { metadata.toMetadata(any(), any(), any()) } returns chat
    }

    private fun serverCopy(content: List<MessageContent>, messageId: Long = 10) = ChatMessage(
        messageId = messageId,
        senderId = selfId,
        content = content,
        timestamp = Instant.fromEpochSeconds(2_000),
        unreadSeq = 0,
        eventSequence = 5,
        isFromSelf = true,
    )

    private val delegate by lazy {
        MessagingDelegate(
            chatController = chatController,
            messagingController = controller,
            metadataDataSource = metadata,
            messageDataSource = messages,
            memberDataSource = members,
            notificationManager = mockk(relaxed = true),
            userManager = userManager,
            stateHolder = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
            senderResolver = mockk(relaxed = true),
            outgoing = DmOutgoingEncryption(
                policy = E2eePolicy(),
                crypto = ChatContentCrypto(FakeChatCipher, keys),
                userManager = userManager,
                chatController = chatController,
                metadataDataSource = metadata,
                memberDataSource = members,
            ),
        )
    }

    /** Captures what the send put on the wire and answers with the server's copy of it. */
    private fun answerSends(): MutableList<List<MessageContent>> {
        val wires = mutableListOf<List<MessageContent>>()
        coEvery { messages.insertPending(chatId, any(), selfId) } answers {
            PendingMessage(serverCopy(secondArg()), clientMessageId)
        }
        coEvery { messages.retryPending(chatId, any()) } returns clientMessageId
        coEvery { controller.sendMessage(chatId, any(), any()) } answers {
            wires += secondArg<List<MessageContent>>()
            Result.success(serverCopy(secondArg()))
        }
        return wires
    }

    private suspend fun openAsPeer(wire: List<MessageContent>): MessageContent {
        val sealed = assertIs<MessageContent.Encrypted>(wire.single())
        val opened = peerCrypto.open(chatId, selfId = peerId, peerId = selfId, senderId = selfId, encrypted = sealed)
        return assertIs<OpenedContent.Plaintext>(opened).content
    }

    @Test
    fun `text in an encrypted DM goes out sealed and is stored as the plaintext`() = runTest {
        storedChat(chat())
        val wires = answerSends()

        delegate.sendMessage(chatId, "hello", replyToMessageId = null).getOrThrow()

        assertEquals(MessageContent.Text("hello"), openAsPeer(wires.single()))
        coVerify { messages.insertPending(chatId, listOf(MessageContent.Text("hello")), selfId) }
        val stored = slot<ChatMessage>()
        coVerify { messages.confirmPending(chatId, clientMessageId, capture(stored)) }
        assertEquals(listOf(MessageContent.Text("hello")), stored.captured.content)
        assertEquals(MessageEncryption.Decrypted(wires.single().single() as MessageContent.Encrypted), stored.captured.encryption)
    }

    @Test
    fun `a reply is sealed whole, quote id and all`() = runTest {
        storedChat(chat())
        val wires = answerSends()

        delegate.sendMessage(chatId, "yes", replyToMessageId = 4).getOrThrow()

        assertEquals(
            MessageContent.Reply(repliedMessageId = 4, content = listOf(MessageContent.Text("yes"))),
            openAsPeer(wires.single()),
        )
    }

    private val photo = MessageContent.Media(
        items = listOf(
            MediaItem(
                listOf(
                    MediaItemRendition(
                        role = MediaItemRendition.Role.ORIGINAL,
                        blobId = BlobId(ByteArray(16) { it.toByte() }),
                        blob = BlobMetadata(
                            mimeType = "image/jpeg",
                            sizeBytes = 10,
                            downloadUrl = "",
                            image = ImageMetadata(width = 4, height = 3, blurhash = ""),
                        ),
                    )
                )
            )
        ),
        caption = null,
    )

    private fun outgoing() = DmOutgoingEncryption(
        policy = E2eePolicy(),
        crypto = ChatContentCrypto(FakeChatCipher, keys),
        userManager = userManager,
        chatController = chatController,
        metadataDataSource = metadata,
        memberDataSource = members,
    )

    @Test
    fun `a photo sealed for this chat goes out sealed`() = runTest {
        storedChat(chat())

        val result = outgoing().prepare(chatId, listOf(photo), blobSealedFor = chatId).getOrThrow()

        assertEquals(true, result.isSealed)
        assertEquals(photo, openAsPeer(result.wire))
    }

    @Test
    fun `a plain photo is refused in an encrypted chat`() = runTest {
        storedChat(chat())

        assertEquals(true, outgoing().prepare(chatId, listOf(photo), blobSealedFor = null).isFailure)
    }

    @Test
    fun `a sealed photo is refused in a chat that does not encrypt, or sealed for another chat`() = runTest {
        storedChat(chat(useE2ee = false))
        assertEquals(true, outgoing().prepare(chatId, listOf(photo), blobSealedFor = chatId).isFailure)

        storedChat(chat())
        val other = ChatId(ByteArray(32) { 4 })
        assertEquals(true, outgoing().prepare(chatId, listOf(photo), blobSealedFor = other).isFailure)
    }

    @Test
    fun `a plain photo goes out plain in a chat that does not encrypt`() = runTest {
        storedChat(chat(useE2ee = false))

        val result = outgoing().prepare(chatId, listOf(photo), blobSealedFor = null).getOrThrow()

        assertEquals(false, result.isSealed)
    }

    @Test
    fun `a DM without the flag goes out in plaintext`() = runTest {
        storedChat(chat(useE2ee = false))
        val wires = answerSends()

        delegate.sendMessage(chatId, "hello", replyToMessageId = null).getOrThrow()

        assertEquals(listOf(MessageContent.Text("hello")), wires.single())
        val stored = slot<ChatMessage>()
        coVerify { messages.confirmPending(chatId, clientMessageId, capture(stored)) }
        assertEquals(null, stored.captured.encryption)
    }

    @Test
    fun `the flipcash chat never encrypts`() = runTest {
        storedChat(chat(peer = flipcashId))
        val wires = answerSends()

        delegate.sendMessage(chatId, "hello", replyToMessageId = null).getOrThrow()

        assertEquals(listOf(MessageContent.Text("hello")), wires.single())
    }

    @Test
    fun `a group never encrypts`() = runTest {
        storedChat(chat(type = ChatType.GROUP))
        val wires = answerSends()

        delegate.sendMessage(chatId, "hello", replyToMessageId = null).getOrThrow()

        assertEquals(listOf(MessageContent.Text("hello")), wires.single())
    }

    @Test
    fun `a chat that isn't stored is read from the server`() = runTest {
        storedChat(null)
        coEvery { chatController.getChat(chatId, any()) } returns Result.success(chat())
        val wires = answerSends()

        delegate.sendMessage(chatId, "hello", replyToMessageId = null).getOrThrow()

        assertEquals(MessageContent.Text("hello"), openAsPeer(wires.single()))
    }

    @Test
    fun `a chat that can't be read fails the send instead of sending plaintext`() = runTest {
        storedChat(null)
        coEvery { chatController.getChat(chatId, any()) } returns Result.failure(GetChatError.Other())
        answerSends()

        val result = delegate.sendMessage(chatId, "hello", replyToMessageId = null)

        assertEquals(true, result.isFailure)
        coVerify(exactly = 0) { controller.sendMessage(any(), any(), any()) }
        coVerify { messages.failPending(chatId, clientMessageId) }
    }

    @Test
    fun `a failed key fetch fails the send so it can be retried`() = runTest {
        storedChat(chat())
        keys.peerFails = IOException("offline")
        answerSends()

        val result = delegate.sendMessage(chatId, "hello", replyToMessageId = null)

        assertIs<IOException>(result.exceptionOrNull())
        coVerify(exactly = 0) { controller.sendMessage(any(), any(), any()) }
        coVerify { messages.failPending(chatId, clientMessageId) }
    }

    @Test
    fun `EncryptionNotAllowed fails the send so it can be retried`() = runTest {
        storedChat(chat())
        answerSends()
        coEvery { controller.sendMessage(chatId, any(), any()) } returns
            Result.failure(SendMessageError.EncryptionNotAllowed())

        val result = delegate.sendMessage(chatId, "hello", replyToMessageId = null)

        assertIs<SendMessageError.EncryptionNotAllowed>(result.exceptionOrNull())
        coVerify { messages.failPending(chatId, clientMessageId) }
    }

    @Test
    fun `a retry seals the stored plaintext again`() = runTest {
        storedChat(chat())
        val wires = answerSends()

        delegate.retryMessage(chatId, "09", listOf(MessageContent.Text("again"))).getOrThrow()

        assertEquals(MessageContent.Text("again"), openAsPeer(wires.single()))
        val stored = slot<ChatMessage>()
        coVerify { messages.confirmPending(chatId, clientMessageId, capture(stored)) }
        assertEquals(listOf(MessageContent.Text("again")), stored.captured.content)
    }

    private fun answerEdits(original: ChatMessage): MutableList<List<MessageContent>> {
        val wires = mutableListOf<List<MessageContent>>()
        coEvery { messages.getMessage(chatId, original.messageId) } returns original
        coEvery { controller.editMessage(chatId, original.messageId, any(), original.eventSequence) } answers {
            wires += thirdArg<List<MessageContent>>()
            Result.success(original.copy(content = thirdArg(), eventSequence = original.eventSequence + 1))
        }
        return wires
    }

    @Test
    fun `an encrypted message stays encrypted when edited after the chat stops encrypting`() = runTest {
        storedChat(chat(useE2ee = false))
        val original = serverCopy(listOf(MessageContent.Text("before")))
            .copy(encryption = MessageEncryption.Decrypted(MessageContent.Encrypted(1, ByteArray(24), ByteArray(8))))
        val wires = answerEdits(original)

        delegate.editMessage(chatId, original.messageId, "after").getOrThrow()

        assertEquals(MessageContent.Text("after"), openAsPeer(wires.single()))
        val stored = slot<List<ChatMessage>>()
        coVerify { messages.upsert(chatId, capture(stored)) }
        assertEquals(listOf(MessageContent.Text("after")), stored.captured.single().content)
        assertEquals(MessageEncryption.Decrypted(wires.single().single() as MessageContent.Encrypted), stored.captured.single().encryption)
    }

    @Test
    fun `a plaintext message is encrypted when edited in a chat that now encrypts`() = runTest {
        storedChat(chat())
        val original = serverCopy(listOf(MessageContent.Text("before")))
        val wires = answerEdits(original)

        delegate.editMessage(chatId, original.messageId, "after").getOrThrow()

        assertEquals(MessageContent.Text("after"), openAsPeer(wires.single()))
    }

    @Test
    fun `an edit that can't be sealed is not sent and its overlay comes down`() = runTest {
        storedChat(chat())
        keys.peerFails = IOException("offline")
        val original = serverCopy(listOf(MessageContent.Text("before")))
        answerEdits(original)

        val result = delegate.editMessage(chatId, original.messageId, "after")

        assertIs<IOException>(result.exceptionOrNull())
        coVerify(exactly = 0) { controller.editMessage(any(), any(), any(), any()) }
        assertEquals(emptyMap(), delegate.observePendingMutations(chatId).first())
    }
}
