package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessagePointer
import com.flipcash.services.models.chat.PointerType
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The READ pointer is written locally the moment a message is seen and reported to the server
 * afterwards, and nothing retries a report that fails. The local copy therefore survives — the
 * member row keeps whichever pointer is further ahead — while the server's stays behind, and every
 * other device, plus the pushes this one receives, goes on treating the chat as unread.
 *
 * A feed payload is the server's own copy, so the sync is where the two can be compared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedSyncReadPointerReportTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val chatId = ChatId("aabbccdd")

    private fun member(pointer: Long) = ChatMember(
        userId = selfId,
        userProfile = UserProfile.Empty,
        pointers = listOf(
            MessagePointer(
                type = PointerType.READ,
                userId = selfId,
                value = pointer,
                timestamp = Instant.fromEpochSeconds(1_000),
            )
        ),
    )

    private class Harness(serverPointer: Long, localPointer: Long, selfId: List<Byte>, chatId: ChatId) {
        val chats = listOf(
            ChatMetadata(
                chatId = chatId,
                type = ChatType.TIP_DM,
                members = listOf(
                    ChatMember(
                        userId = selfId,
                        userProfile = UserProfile.Empty,
                        pointers = listOf(
                            MessagePointer(
                                type = PointerType.READ,
                                userId = selfId,
                                value = serverPointer,
                                timestamp = Instant.fromEpochSeconds(1_000),
                            )
                        ),
                    )
                ),
                lastMessage = null,
                lastActivity = Instant.fromEpochSeconds(1_000),
            )
        )

        val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also { source ->
            coEvery { source.getSelfReadPointer(any(), any()) } returns localPointer
        }

        val chatController = mockk<ChatController>(relaxed = true).also { controller ->
            coEvery { controller.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
                Result.success(ChatFeedPage(emptyList(), null, false))
            coEvery { controller.getDmChatFeed(ChatType.TIP_DM, any()) } returns
                Result.success(ChatFeedPage(chats, null, false))
        }

        val delegate = FeedSyncDelegate(
            chatController = chatController,
            metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true),
            messageDataSource = mockk<ChatMessageDataSource>(relaxed = true),
            memberDataSource = memberDataSource,
            stateHolder = ChatStateHolder(),
            userManager = mockk<UserManager>(relaxed = true).also {
                every { it.accountId } returns selfId
            },
        )

        fun sync(scope: TestScope): List<FeedSyncDelegate.Event> {
            val events = mutableListOf<FeedSyncDelegate.Event>()
            delegate.events.onEach { events += it }.launchIn(scope.backgroundScope)
            delegate.initialize(scope.backgroundScope)
            delegate.syncFeed()
            scope.runCurrent()
            return events
        }
    }

    @Test
    fun `a read the server never took is re-reported`() = runTest {
        val events = Harness(serverPointer = 3, localPointer = 9, selfId = selfId, chatId = chatId)
            .sync(this)

        assertEquals(
            listOf(FeedSyncDelegate.Event.ReadPointerUnreported(chatId, 9)),
            events.filterIsInstance<FeedSyncDelegate.Event.ReadPointerUnreported>(),
            "the local pointer is ahead of the server's, so the advance never landed",
        )
    }

    @Test
    fun `a read the server already has is not re-reported`() = runTest {
        val events = Harness(serverPointer = 9, localPointer = 9, selfId = selfId, chatId = chatId)
            .sync(this)

        assertTrue(
            events.none { it is FeedSyncDelegate.Event.ReadPointerUnreported },
            "the server is level with the client; there is nothing to report",
        )
    }
}
