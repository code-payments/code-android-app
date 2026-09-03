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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Deleting the newest message must not leave the conversation list reading "Message deleted" with
 * an unread splat beside it. The feed row is built from the newest message that still has content,
 * so the preview falls back to the one before the tombstone and the unread check — which compares
 * that same message against the READ pointer — clears with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedDeletedMessageTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val chatId = ChatId("aabbccdd")

    private fun message(messageId: Long, content: MessageContent) = ChatMessage(
        messageId = messageId,
        senderId = otherId,
        content = listOf(content),
        timestamp = Instant.fromEpochSeconds(messageId),
        unreadSeq = messageId,
    )

    /** [readPointer] is the signed-in user's READ watermark; the counterparty is addressable. */
    private class Harness(selfId: List<Byte>, otherId: List<Byte>, chatId: ChatId, readPointer: Long) {
        private val entity = ChatMetadataEntity(
            chatIdHex = "aabbccdd",
            chatType = ChatType.CONTACT_DM.name,
            lastActivityEpochMs = 2_000,
            lastMessageId = 2,
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
                    )
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

        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

        private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
            every { source.observeAll() } returns flowOf(listOf(entity))
            every { source.toMetadata(any(), any(), any()) } answers {
                ChatMetadata(
                    chatId = chatId,
                    type = ChatType.CONTACT_DM,
                    members = secondArg(),
                    lastMessage = thirdArg(),
                    lastActivity = Instant.fromEpochSeconds(2),
                )
            }
        }

        private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also { source ->
            every { source.observeAll() } returns flowOf(mapOf(entity.chatIdHex to members))
        }

        val delegate = FeedSyncDelegate(
            chatController = mockk<ChatController>(relaxed = true),
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = memberDataSource,
            stateHolder = ChatStateHolder(),
            userManager = mockk<UserManager>(relaxed = true).also {
                every { it.accountId } returns selfId
                every { it.profile } returns null
            },
        )

        suspend fun summary(scope: TestScope): ChatSummary? {
            delegate.initialize(scope.backgroundScope)
            delegate.observeFeedFromDb()
            scope.runCurrent()
            return delegate.feed(ChatType.CONTACT_DM).first().firstOrNull()
        }
    }

    @Test
    fun `the preview falls back to the newest message that still has content`() = runTest {
        val harness = Harness(selfId, otherId, chatId, readPointer = 1)
        // Message 2 was deleted; the visible newest is message 1.
        coEvery { harness.messageDataSource.getLatestVisible(any()) } returns
            message(1, MessageContent.Text("still here"))

        val summary = harness.summary(this)

        assertEquals(1L, summary?.metadata?.lastMessage?.messageId)
        assertEquals(
            MessageContent.Text("still here"),
            summary?.metadata?.lastMessage?.content?.first(),
        )
    }

    @Test
    fun `deleting the only unread message clears the splat`() = runTest {
        // The READ pointer sits at 1: message 2 arrived unread, then was deleted.
        val harness = Harness(selfId, otherId, chatId, readPointer = 1)
        coEvery { harness.messageDataSource.getLatestVisible(any()) } returns
            message(1, MessageContent.Text("read already"))

        assertEquals(0, harness.summary(this)?.unreadCount)
    }

    @Test
    fun `an unread message older than the tombstone keeps the splat`() = runTest {
        // Nothing has been read, so the message the preview falls back to is itself unread.
        val harness = Harness(selfId, otherId, chatId, readPointer = 0)
        coEvery { harness.messageDataSource.getLatestVisible(any()) } returns
            message(1, MessageContent.Text("never read"))

        assertEquals(1, harness.summary(this)?.unreadCount)
    }

    @Test
    fun `a chat with nothing but tombstones has no preview`() = runTest {
        val harness = Harness(selfId, otherId, chatId, readPointer = 0)
        coEvery { harness.messageDataSource.getLatestVisible(any()) } returns null

        val summary = harness.summary(this)

        assertNull(summary?.metadata?.lastMessage)
        assertEquals(0, summary?.unreadCount)
    }
}
