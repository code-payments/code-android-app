package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMessage
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A feed sync writes each chat's `lastMessage` and the server's `latestEventSequence` before it
 * decides whether that chat needs catching up. These tests hold the decision to what the cache held
 * *before* the sync wrote to it.
 *
 * The user-visible failure: after a re-login the chat cache is empty (logout clears it via
 * `ChatCoordinator.reset`), the sync repopulates one message per chat, and anything derived from
 * chat history — the "send a tip" onboarding milestone reads an outgoing TIPPED message out of it —
 * silently reads as if it never happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedSyncCatchUpTest {

    private val selfId = listOf<Byte>(1, 2, 3)
    private val otherId = listOf<Byte>(4, 5, 6)
    private val chatId = ChatId("aabbccdd")

    private fun message(
        messageId: Long,
        senderId: List<Byte>,
        eventSequence: Long = messageId,
    ) = ChatMessage(
        messageId = messageId,
        senderId = senderId,
        content = listOf(MessageContent.Text("hi")),
        timestamp = Instant.fromEpochSeconds(1_000 + messageId),
        unreadSeq = messageId,
        eventSequence = eventSequence,
    )

    private fun metadata(
        lastMessage: ChatMessage?,
        latestEventSequence: Long = 0,
    ) = ChatMetadata(
        chatId = chatId,
        type = ChatType.TIP_DM,
        members = emptyList(),
        lastMessage = lastMessage,
        lastActivity = Instant.fromEpochSeconds(1_000),
        latestEventSequence = latestEventSequence,
    )

    /**
     * Stands in for the per-user Room database: what the sync writes is what a later read sees.
     * Relaxed mocks would answer `false`/`0` regardless of the writes, which is exactly the coupling
     * under test.
     */
    private class Harness(chats: List<ChatMetadata>, seededChatsWithMessages: Set<ChatId> = emptySet()) {
        val chatsWithMessages = seededChatsWithMessages.toMutableSet()
        val storedSequences = mutableMapOf<ChatId, Long>()

        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true).also { source ->
            coEvery { source.upsert(any<ChatId>(), any()) } answers {
                chatsWithMessages += firstArg<ChatId>()
            }
            coEvery { source.hasMessages(any()) } answers { firstArg<ChatId>() in chatsWithMessages }
        }

        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
            coEvery { source.upsert(any<List<ChatMetadata>>()) } answers {
                firstArg<List<ChatMetadata>>().forEach { storedSequences[it.chatId] = it.latestEventSequence }
            }
            coEvery { source.getLatestEventSequence(any()) } answers {
                storedSequences[firstArg<ChatId>()] ?: 0L
            }
        }

        val chatController = mockk<ChatController>(relaxed = true).also { controller ->
            coEvery { controller.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
                Result.success(ChatFeedPage(emptyList(), null, false))
            coEvery { controller.getDmChatFeed(ChatType.TIP_DM, any()) } returns
                Result.success(ChatFeedPage(chats, null, false))
        }

        val delegate = FeedSyncDelegate(
            chatController = chatController,
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = mockk<ChatMemberDataSource>(relaxed = true),
            stateHolder = ChatStateHolder(),
            userManager = mockk<UserManager>(relaxed = true),
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
    fun `cold cache loads a chat's history even when the feed carried its last message`() = runTest {
        // The tip the user sent is not the newest message in the chat — the other party replied
        // after it — so the feed's lastMessage alone can never prove the tip happened.
        val harness = Harness(chats = listOf(metadata(lastMessage = message(20, otherId))))

        val events = harness.sync(this)

        assertEquals(
            listOf(FeedSyncDelegate.Event.LoadMessages(chatId), FeedSyncDelegate.Event.CatchUpComplete),
            events,
            "a chat with no cached history must be caught up, not judged by the row the sync just wrote",
        )
    }

    @Test
    fun `warm cache does not refetch history`() = runTest {
        val harness = Harness(
            chats = listOf(metadata(lastMessage = message(20, otherId))),
            seededChatsWithMessages = setOf(chatId),
        )

        val events = harness.sync(this)

        assertEquals(
            listOf(FeedSyncDelegate.Event.CatchUpComplete),
            events,
            "a chat already backed by cached history needs no catch-up, only the terminal marker",
        )
    }

    @Test
    fun `a local sequence behind the server's triggers a delta sync`() = runTest {
        val harness = Harness(
            chats = listOf(metadata(lastMessage = message(20, otherId), latestEventSequence = 99)),
            seededChatsWithMessages = setOf(chatId),
        ).apply { storedSequences[chatId] = 42 }

        val events = harness.sync(this)

        assertEquals(
            listOf(
                FeedSyncDelegate.Event.DeltaSyncNeeded(chatId, afterSequence = 42),
                FeedSyncDelegate.Event.CatchUpComplete,
            ),
            events,
            "the gap must be measured against the sequence the cache held before the sync overwrote it",
        )
    }

    /**
     * The event carrying its own `afterSequence` is the whole point: the delta consumer reads
     * `chat_metadata` when not given one, and by then this sync has already stamped the server's
     * sequence onto that row — so a re-read would ask the server for everything after its own
     * latest event and get nothing back.
     */
    @Test
    fun `the delta request starts from the pre-sync sequence, not the row the sync wrote`() = runTest {
        val harness = Harness(
            chats = listOf(metadata(lastMessage = message(20, otherId), latestEventSequence = 99)),
            seededChatsWithMessages = setOf(chatId),
        ).apply { storedSequences[chatId] = 42 }

        val event = harness.sync(this)
            .filterIsInstance<FeedSyncDelegate.Event.DeltaSyncNeeded>()
            .single()

        assertEquals(
            99L,
            harness.metadataDataSource.getLatestEventSequence(chatId),
            "precondition: the sync has overwritten the stored sequence with the server's",
        )
        assertEquals(
            42L,
            event.afterSequence,
            "the delta must be requested from 42, or the backfill silently fetches nothing",
        )
    }

    /**
     * The marker has to be last, not merely present: hydration is declared when it is routed, and a
     * marker that overtook a pending catch-up would declare the cache complete while the backfill
     * that completes it is still outstanding.
     */
    @Test
    fun `the catch-up marker is the last event of a successful sync`() = runTest {
        val harness = Harness(chats = listOf(metadata(lastMessage = message(20, otherId))))

        val events = harness.sync(this)

        assertEquals(FeedSyncDelegate.Event.CatchUpComplete, events.last())
    }

    @Test
    fun `a failed sync emits no marker`() = runTest {
        val harness = Harness(chats = emptyList())
        coEvery { harness.chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
            Result.failure(RuntimeException("offline"))

        val events = harness.sync(this)

        assertEquals(
            emptyList(),
            events,
            "nothing was reconciled, so nothing may report itself reconciled",
        )
    }
}
