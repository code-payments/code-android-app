package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers which chats a feed sync decides to backfill. The decision is keyed on the locally
 * applied event cursor — never on whether the chat holds any message rows, because the sync
 * itself persists every chat's last-message preview as a row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedSyncBackfillTest {

    private fun metadata(hex: String, latestEventSequence: Long) = ChatMetadata(
        chatId = ChatId(hex),
        type = ChatType.CONTACT_DM,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1000),
        latestEventSequence = latestEventSequence,
    )

    /**
     * Runs one feed sync over a single chat whose server head is [serverHead] and whose locally
     * applied cursor is [localCursor], and returns the events it emitted. Every successful sync
     * ends with [FeedSyncDelegate.Event.CatchUpComplete], so that marker closes each expectation
     * below; what varies is the backfill decision in front of it.
     */
    private suspend fun TestScope.backfillEvents(
        serverHead: Long,
        localCursor: Long,
    ): List<FeedSyncDelegate.Event> {
        val chat = metadata(CHAT_HEX, serverHead)

        val chatController = mockk<ChatController>(relaxed = true)
        coEvery { chatController.getDmChatFeed(ChatType.CONTACT_DM, any()) } returns
            Result.success(ChatFeedPage(listOf(chat), null, false))
        coEvery { chatController.getDmChatFeed(ChatType.TIP_DM, any()) } returns
            Result.success(ChatFeedPage(emptyList(), null, false))

        val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
        coEvery { metadataDataSource.getLatestEventSequence(chat.chatId) } returns localCursor

        // Nothing here stubs `hasMessages`: the decision must not consult it. The sync persists
        // every chat's last-message preview, so it would answer true even for a chat whose
        // transcript was never pulled.
        val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

        val delegate = FeedSyncDelegate(
            chatController = chatController,
            metadataDataSource = metadataDataSource,
            messageDataSource = messageDataSource,
            memberDataSource = mockk(relaxed = true),
            stateHolder = mockk(relaxed = true),
            userManager = mockk(relaxed = true),
        )

        val received = mutableListOf<FeedSyncDelegate.Event>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.events.collect { received += it }
        }
        delegate.performFeedSync()
        runCurrent()
        collector.cancel()
        return received
    }

    @Test
    fun `chat that was never fetched is backfilled with its newest page`() = runTest {
        // The sync has just written this chat's last-message preview, so it holds a message row —
        // only the unseated cursor reveals that its transcript was never pulled.
        val events = backfillEvents(serverHead = 12, localCursor = 0)

        assertEquals(
            listOf(
                FeedSyncDelegate.Event.LoadMessages(ChatId(CHAT_HEX)),
                FeedSyncDelegate.Event.CatchUpComplete,
            ),
            events,
        )
    }

    @Test
    fun `chat that was never fetched is never delta synced from zero`() = runTest {
        // A delta after sequence 0 re-pulls the entire history instead of the newest page.
        val events = backfillEvents(serverHead = 0, localCursor = 0)

        assertEquals(
            listOf(
                FeedSyncDelegate.Event.LoadMessages(ChatId(CHAT_HEX)),
                FeedSyncDelegate.Event.CatchUpComplete,
            ),
            events,
        )
    }

    @Test
    fun `chat behind the server head catches up with a delta sync`() = runTest {
        val events = backfillEvents(serverHead = 12, localCursor = 5)

        assertEquals(
            listOf(
                FeedSyncDelegate.Event.DeltaSyncNeeded(ChatId(CHAT_HEX)),
                FeedSyncDelegate.Event.CatchUpComplete,
            ),
            events,
        )
    }

    @Test
    fun `chat already at the server head needs no backfill`() = runTest {
        val events = backfillEvents(serverHead = 12, localCursor = 12)

        assertEquals(listOf(FeedSyncDelegate.Event.CatchUpComplete), events)
    }

    @Test
    fun `chat ahead of a stale feed head needs no backfill`() = runTest {
        // The stream can apply events the feed page predates; that is not a gap.
        val events = backfillEvents(serverHead = 12, localCursor = 20)

        assertEquals(listOf(FeedSyncDelegate.Event.CatchUpComplete), events)
    }

    private companion object {
        const val CHAT_HEX = "aabbccdd"
    }
}
