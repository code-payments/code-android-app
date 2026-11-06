package com.flipcash.shared.chat

import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.VerifiableContactMethod
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The conversation list takes its previews from the stream that tells it they changed, and reads
 * every row's unread stamp in one call, rather than querying the previews a second time and the
 * stamps once per chat on every rebuild. The list it builds is the same either way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedObservedPreviewTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val chatHex = "aabbccdd"
    private val readPointer = 2L

    private fun message(messageId: Long, text: String) = ChatMessage(
        messageId = messageId,
        senderId = otherId,
        content = listOf(MessageContent.Text(text)),
        timestamp = Instant.fromEpochSeconds(messageId),
        unreadSeq = messageId,
    )

    private inner class Harness(observed: Map<String, ChatMessage>?, direct: Map<String, ChatMessage>) {
        private val entity = ChatMetadataEntity(
            chatIdHex = chatHex,
            chatType = ChatType.CONTACT_DM.name,
            lastActivityEpochMs = 5_000,
            lastMessageId = 5,
        )

        private val members = listOf(
            ChatMember(
                userId = selfId,
                userProfile = UserProfile.Empty,
                pointers = listOf(
                    MessagePointer(
                        type = PointerType.READ,
                        userId = selfId,
                        value = readPointer,
                        timestamp = Instant.fromEpochSeconds(1_000),
                    ),
                ),
            ),
            ChatMember(
                userId = otherId,
                userProfile = UserProfile.Empty.copy(
                    displayName = "Ada",
                    phoneNumber = VerifiableContactMethod("+15551234567", verified = true),
                ),
                pointers = emptyList(),
            ),
        )

        /** What the preview stream emits; null leaves it silent, as a database not yet open. */
        val previews = observed?.let { MutableStateFlow(it) }

        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also {
            if (previews != null) every { it.observeLatestVisibleByChat() } returns previews
            coEvery { it.getLatestVisibleByChat() } returns direct
            coEvery { it.getUnreadSeqs(any()) } answers {
                firstArg<Collection<Pair<String, Long>>>().associateWith { key -> key.second }
            }
        }

        val delegate = FeedSyncDelegate(
            messagingController = mockk(relaxed = true),
            chatController = mockk<ChatController>(relaxed = true),
            metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
                every { source.observeAll() } returns flowOf(listOf(entity))
                every { source.toMetadata(any(), any(), any()) } answers {
                    ChatMetadata(
                        chatId = ChatId(chatHex),
                        type = ChatType.CONTACT_DM,
                        members = secondArg(),
                        lastMessage = thirdArg(),
                        lastActivity = Instant.fromEpochSeconds(5),
                    )
                }
            },
            messageDataSource = messageDataSource,
            memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also { source ->
                every { source.observeAll() } returns flowOf(mapOf(chatHex to members))
            },
            stateHolder = ChatStateHolder(),
            userManager = mockk<UserManager>(relaxed = true).also {
                every { it.accountId } returns selfId
                every { it.profile } returns null
            },
        )

        fun start(scope: TestScope) {
            delegate.initialize(scope.backgroundScope)
            delegate.observeFeedFromDb()
            scope.runCurrent()
        }

        suspend fun summary(): ChatSummary? = delegate.feed(ChatType.CONTACT_DM).first().firstOrNull()
    }

    @Test
    fun `a preview change rebuilds from the streamed previews without querying them again`() = runTest {
        val harness = Harness(
            observed = mapOf(chatHex to message(4, "four")),
            direct = mapOf(chatHex to message(1, "stale direct read")),
        )
        harness.start(this)
        coVerify(atMost = 1) { harness.messageDataSource.getLatestVisibleByChat() }

        harness.previews!!.value = mapOf(chatHex to message(5, "five"))
        runCurrent()

        assertEquals(5L, harness.summary()?.metadata?.lastMessage?.messageId)
        assertEquals(3, harness.summary()?.unreadCount)
        coVerify(atMost = 1) { harness.messageDataSource.getLatestVisibleByChat() }
    }

    @Test
    fun `every row's stamp is read in one call`() = runTest {
        val harness = Harness(observed = mapOf(chatHex to message(5, "five")), direct = emptyMap())
        harness.start(this)

        assertEquals(3, harness.summary()?.unreadCount)
        coVerify { harness.messageDataSource.getUnreadSeqs(listOf(chatHex to readPointer)) }
        coVerify(exactly = 0) { harness.messageDataSource.getUnreadSeq(any(), any()) }
    }

    @Test
    fun `the list built from streamed previews matches the one built from a direct read`() = runTest {
        val previews = mapOf(chatHex to message(5, "five"))
        val streamed = Harness(observed = previews, direct = emptyMap())
        val direct = Harness(observed = null, direct = previews)
        streamed.start(this)
        direct.start(this)

        assertEquals(direct.summary(), streamed.summary())
        assertEquals(5L, streamed.summary()?.metadata?.lastMessage?.messageId)
    }
}
