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
import io.mockk.coVerify
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
 * A feed sync writes each chat's `lastMessage` before it decides whether that chat needs catching
 * up, so the presence of message rows says nothing about whether a transcript was ever pulled.
 * These tests hold the decision to the applied event cursor instead, and hold the terminal marker
 * to its position after it.
 *
 * The user-visible failure: after a re-login the chat cache is empty (logout clears it via
 * `ChatCoordinator.reset`), the sync repopulates one message per chat, and anything derived from
 * chat history — the "send a tip" onboarding milestone reads an outgoing TIPPED message out of it —
 * silently reads as if it never happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedSyncCatchUpTest {

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
     *
     * `upsert` deliberately leaves [seededCursors] alone. That is the DAO's contract — the feed
     * payload carries the server's head, and `ChatMetadataDao.upsert` refreshes only the columns
     * the server owns, so a chat's applied cursor survives a sync (see `ChatMetadataDaoTest`).
     */
    private class Harness(chats: List<ChatMetadata>, seededCursors: Map<ChatId, Long> = emptyMap()) {
        val storedSequences = seededCursors.toMutableMap()

        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also { source ->
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
            chats = listOf(metadata(lastMessage = message(20, otherId), latestEventSequence = 20)),
            seededCursors = mapOf(chatId to 20L),
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
            seededCursors = mapOf(chatId to 42L),
        )

        val events = harness.sync(this)

        assertEquals(
            listOf(
                FeedSyncDelegate.Event.DeltaSyncNeeded(chatId),
                FeedSyncDelegate.Event.CatchUpComplete,
            ),
            events,
            "the gap must be measured against the sequence the cache holds, not against message rows",
        )
    }

    /**
     * The delta consumer reads the cursor out of `chat_metadata` for itself, which is only safe
     * because a feed sync never seats one: a chat is caught up by a message load or by an applied
     * delta, and by nothing the feed reports about the server's head.
     */
    @Test
    fun `a feed sync never advances the applied cursor`() = runTest {
        val harness = Harness(
            chats = listOf(metadata(lastMessage = message(20, otherId), latestEventSequence = 99)),
            seededCursors = mapOf(chatId to 42L),
        )

        harness.sync(this)

        coVerify(exactly = 0) { harness.metadataDataSource.updateLatestEventSequence(any(), any()) }
        assertEquals(
            42L,
            harness.metadataDataSource.getLatestEventSequence(chatId),
            "the delta must still start from 42, or the backfill silently fetches nothing",
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
