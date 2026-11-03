package com.flipcash.shared.chat.share

import com.flipcash.shared.chat.testing.tempRoot
import com.flipcash.app.blob.PickedPhoto
import com.flipcash.shared.chat.testing.FakePhoto
import com.flipcash.app.blob.BlobStorageCoordinator
import com.flipcash.app.blob.ChatMediaEncoder
import com.flipcash.app.blob.ChatMediaSealing
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.PendingMessage
import com.flipcash.services.chat.ChatContentCrypto
import com.flipcash.services.chat.ChatKeySource
import com.flipcash.services.chat.E2eePolicy
import com.flipcash.services.chat.GroupContentCrypto
import com.flipcash.services.chat.GroupKeySource
import com.flipcash.services.chat.FakeChatCipher
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.blob.EncryptedConstraints
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ClientMessageId
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.ChatCoordinator
import com.flipcash.shared.chat.internal.DmOutgoingEncryption
import com.flipcash.shared.chat.internal.delegates.MessagingDelegate
import com.flipcash.shared.chat.media.ChatMediaUploadState
import com.flipcash.shared.chat.media.ChatMediaUploads
import com.getcode.ed25519kmp.KeyPair
import com.getcode.opencode.model.core.ID
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * [ChatShareGateway] over the real encryption layer ([DmOutgoingEncryption] with the real
 * [E2eePolicy]), so what the share picker sends is held to the same rule as typing in the chat:
 * a chat that encrypts gets sealed text and sealed photo uploads, one that doesn't gets plain ones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatShareGatewayTest {

    private val root = tempRoot()

    private val encrypted = ChatId(ByteArray(32) { 3 })
    private val otherEncrypted = ChatId(ByteArray(32) { 5 })
    private val plain = ChatId(ByteArray(32) { 4 })

    private val selfId: ID = List(16) { 1 }
    private val peerId: ID = List(16) { 2 }
    private val selfKeys = KeyPair(publicKey = ByteArray(32) { 11 }, privateKey = ByteArray(64) { 12 })

    private val keys = object : ChatKeySource {
        override fun ownKeyPair() = selfKeys
        override suspend fun peerPublicKey(userId: ID) = Result.success(ByteArray(32) { 21 })
    }

    private val chats = mapOf(
        encrypted to chat(encrypted, useE2ee = true),
        otherEncrypted to chat(otherEncrypted, useE2ee = true),
        plain to chat(plain, useE2ee = false),
    )

    private fun chat(id: ChatId, useE2ee: Boolean) = ChatMetadata(
        chatId = id,
        type = ChatType.CONTACT_DM,
        members = listOf(member(selfId), member(peerId)),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(0),
        useE2ee = useE2ee,
    )

    private fun member(id: ID) = ChatMember(userId = id, userProfile = UserProfile.Empty, pointers = emptyList())

    private val userManager = mockk<UserManager>(relaxed = true).also { every { it.accountId } returns selfId }
    private val chatController = mockk<ChatController>(relaxed = true)
    private val metadata = mockk<ChatMetadataDataSource>(relaxed = true).also { m ->
        every { m.observeById(any()) } answers { flowOf(mockk<ChatMetadataEntity>()) }
        every { m.toMetadata(any(), any(), any()) } returns chats.getValue(encrypted)
    }
    private val members = mockk<ChatMemberDataSource>(relaxed = true)
    private val messages = mockk<ChatMessageDataSource>(relaxed = true)
    private val messaging = mockk<ChatMessagingController>(relaxed = true)

    /** The wire content of each text send, by chat. */
    private val wires = LinkedHashMap<ChatId, List<MessageContent>>()

    private val outgoing = DmOutgoingEncryption(
        policy = E2eePolicy(),
        crypto = ChatContentCrypto(FakeChatCipher, keys),
        // Every chat shared to here is a DM or public group, so no group key is ever asked for.
        groupCrypto = GroupContentCrypto(FakeChatCipher, GroupKeySource { error("no private groups") }),
        userManager = userManager,
        chatController = chatController,
        metadataDataSource = metadata,
        memberDataSource = members,
    )

    private fun serverCopy(content: List<MessageContent>) = ChatMessage(
        messageId = 10, senderId = selfId, content = content,
        timestamp = Instant.fromEpochSeconds(2_000), unreadSeq = 0, eventSequence = 5, isFromSelf = true,
    )

    private val encoder = mockk<ChatMediaEncoder> {
        coEvery { encode(any(), any()) } returns Result.success(ChatMediaEncoder.Encoded(byteArrayOf(1, 2, 3), 100, 50))
    }

    /** The sealing each upload was handed, in upload order. */
    private val sealings = ArrayList<ChatMediaSealing?>()
    private val blobs = mockk<BlobStorageCoordinator> {
        every { policy } returns MutableStateFlow(
            UploadPolicy("v1", 1.hours, listOf(MimeTypeConstraints("image/jpeg", 1_000_000, null)), EncryptedConstraints(1_000_000, null)),
        )
        coEvery { storeChatMediaUnfinalized(any(), any(), any(), any()) } coAnswers {
            sealings += secondArg<ChatMediaSealing?>()
            Result.success(BlobId(byteArrayOf(9, 9)))
        }
        coEvery { awaitChatMediaReady(any()) } returns Result.success(BlobId(byteArrayOf(9, 9)))
    }

    private fun TestScope.gateway(): Pair<ChatShareGateway, ChatMediaUploads> {
        every { metadata.toMetadata(any(), any(), any()) } answers { chats.getValue(currentChat) }
        coEvery { members.getMembersForChat(any<ChatId>()) } coAnswers { chats.getValue(arg<ChatId>(0)).members }
        every { metadata.observeById(any()) } answers {
            currentChat = arg<ChatId>(0)
            flowOf(mockk<ChatMetadataEntity>())
        }
        coEvery { messages.insertPending(any(), any(), any()) } answers {
            PendingMessage(serverCopy(secondArg()), ClientMessageId(ByteArray(16) { 9 }))
        }
        coEvery { messaging.sendMessage(any(), any(), any()) } answers {
            wires[arg<ChatId>(0)] = arg<List<MessageContent>>(1)
            Result.success(serverCopy(secondArg()))
        }
        val delegate = MessagingDelegate(
            chatController = chatController,
            messagingController = messaging,
            metadataDataSource = metadata,
            messageDataSource = messages,
            memberDataSource = members,
            notifications = mockk(relaxed = true),
            userManager = userManager,
            stateHolder = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
            senderResolver = mockk(relaxed = true),
            outgoing = outgoing,
        )
        val coordinator = mockk<ChatCoordinator> {
            coEvery { sendMessage(any(), any(), any()) } coAnswers {
                delegate.sendMessage(firstArg(), secondArg(), thirdArg())
            }
        }
        val uploads = ChatMediaUploads(
            directory = { File(root, "pending-media") },
            encoder = encoder, blobs = blobs, outgoing = outgoing,
            sourceSize = { 4000 to 3000 }, blurhashOf = { null }, photos = { error("tests stage photos directly") },
            scope = backgroundScope, newId = { "chip${next++}" },
        )
        return ChatShareGateway(coordinator, uploads) to uploads
    }

    private var currentChat: ChatId = encrypted
    private var next = 0

    @Test
    fun `text to an encrypting chat is sealed and to a plain chat is not`() = runTest {
        val (gateway, _) = gateway()

        gateway.sendText(encrypted, "hello").getOrThrow()
        gateway.sendText(plain, "hello").getOrThrow()

        assertIs<MessageContent.Encrypted>(wires.getValue(encrypted).single())
        assertEquals(listOf<MessageContent>(MessageContent.Text("hello")), wires.getValue(plain))
    }

    @Test
    fun `a photo is uploaded sealed for the encrypting chat and plain for the other`() = runTest {
        val (gateway, uploads) = gateway()
        val uri = FakePhoto("1")

        val sealedChip = gateway.stagePhoto(encrypted, uri)
        val plainChip = gateway.stagePhoto(plain, uri)
        runCurrent()

        val sealed = assertIs<ChatMediaUploadState.Uploaded>(uploads.current(sealedChip)).photo
        val unsealed = assertIs<ChatMediaUploadState.Uploaded>(uploads.current(plainChip)).photo
        assertEquals(encrypted, sealed.sealedFor)
        assertNull(unsealed.sealedFor)
        assertEquals(listOf(encrypted, null), sealings.map { it?.chatId })
    }

    @Test
    fun `the same image staged for two encrypting chats is sealed separately`() = runTest {
        val (gateway, uploads) = gateway()
        val uri = FakePhoto("1")

        val first = gateway.stagePhoto(encrypted, uri)
        val second = gateway.stagePhoto(otherEncrypted, uri)
        runCurrent()

        assertNotEquals(first, second)
        assertEquals(encrypted, assertIs<ChatMediaUploadState.Uploaded>(uploads.current(first)).photo.sealedFor)
        assertEquals(otherEncrypted, assertIs<ChatMediaUploadState.Uploaded>(uploads.current(second)).photo.sealedFor)
        assertEquals(listOf(encrypted, otherEncrypted), sealings.map { it?.chatId })
    }
}
