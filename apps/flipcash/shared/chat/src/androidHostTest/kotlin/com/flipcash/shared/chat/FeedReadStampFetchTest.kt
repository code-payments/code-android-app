package com.flipcash.shared.chat

import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.GetMessageError
import com.flipcash.services.models.UserProfile
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * A chat's unread count subtracts the stamp on the message its READ pointer names. When the device
 * doesn't store that message, the feed fetches it once and counts exactly, matching iOS.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedReadStampFetchTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val chatHex = "11223344"
    private val readPointer = 5L

    private val entity = ChatMetadataEntity(
        chatIdHex = chatHex,
        chatType = ChatType.GROUP.name,
        lastActivityEpochMs = 2_000,
        lastMessageId = 9,
        isMember = true,
    )

    // Stamped with its own id, so the newest (9) is four past the pointer's message (5).
    private fun message(messageId: Long) = ChatMessage(
        messageId = messageId,
        senderId = otherId,
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(messageId),
        unreadSeq = messageId,
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
        ChatMember(userId = otherId, userProfile = UserProfile.Empty, pointers = emptyList()),
    )

    private inner class Harness(storedStamp: Long?, var fetch: () -> Result<ChatMessage>) {
        private val entities = MutableSharedFlow<List<ChatMetadataEntity>>(replay = 1)
            .apply { tryEmit(listOf(entity)) }

        val messagingController = mockk<ChatMessagingController>().also {
            coEvery { it.getMessage(any(), any(), any()) } answers { fetch() }
        }

        private val delegate = FeedSyncDelegate(
            messagingController = messagingController,
            chatController = mockk<ChatController>(relaxed = true),
            metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
                every { source.observeAll() } returns entities
                every { source.toMetadata(any(), any(), any()) } answers {
                    val row = firstArg<ChatMetadataEntity>()
                    ChatMetadata(
                        chatId = ChatId(row.chatIdHex),
                        type = ChatType.valueOf(row.chatType),
                        members = secondArg(),
                        lastMessage = thirdArg(),
                        lastActivity = Instant.fromEpochMilliseconds(row.lastActivityEpochMs),
                    )
                }
            },
            messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also {
                coEvery { it.getLatestVisibleByChat() } returns mapOf(chatHex to message(9))
                coEvery { it.getUnreadSeq(chatHex, readPointer) } returns storedStamp
            },
            memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also {
                every { it.observeAll() } returns flowOf(mapOf(chatHex to members))
            },
            stateHolder = ChatStateHolder().apply {
                update { it.copy(feedSyncState = FeedSyncState.Synced) }
            },
            userManager = mockk<UserManager>(relaxed = true).also {
                every { it.accountId } returns selfId
                every { it.profile } returns null
            },
        )

        suspend fun count(scope: TestScope): Int? {
            delegate.initialize(scope.backgroundScope)
            delegate.observeFeedFromDb()
            scope.runCurrent()
            return delegate.feed(ChatType.GROUP).first().single().unreadCount
        }

        /** Rebuilds the feed from the database, as any write to the chat's row would. */
        suspend fun rebuild(scope: TestScope): Int? {
            entities.emit(listOf(entity))
            scope.runCurrent()
            return delegate.feed(ChatType.GROUP).first().single().unreadCount
        }
    }

    @Test
    fun `a stored pointer message is counted without a request`() = runTest {
        val harness = Harness(storedStamp = 5, fetch = { error("not fetched") })

        assertEquals(4, harness.count(this))
        coVerify(exactly = 0) { harness.messagingController.getMessage(any(), any(), any()) }
    }

    @Test
    fun `an unstored pointer message is fetched once and counted exactly`() = runTest {
        val harness = Harness(storedStamp = null, fetch = { Result.success(message(5)) })

        assertEquals(4, harness.count(this))
        assertEquals(4, harness.rebuild(this))
        coVerify(exactly = 1) { harness.messagingController.getMessage(ChatId(chatHex), readPointer, any()) }
    }

    @Test
    fun `a pointer message the server can't find stays unknown and isn't asked for again`() = runTest {
        val harness = Harness(storedStamp = null, fetch = { Result.failure(GetMessageError.NotFound()) })

        assertNull(harness.count(this))
        assertNull(harness.rebuild(this))
        coVerify(exactly = 1) { harness.messagingController.getMessage(any(), any(), any()) }
    }

    @Test
    fun `a failed fetch stays unknown and is retried on the next build`() = runTest {
        val harness = Harness(storedStamp = null, fetch = { Result.failure(GetMessageError.Other()) })

        assertNull(harness.count(this))

        harness.fetch = { Result.success(message(5)) }
        assertEquals(4, harness.rebuild(this))
        coVerify(exactly = 2) { harness.messagingController.getMessage(any(), any(), any()) }
    }
}
