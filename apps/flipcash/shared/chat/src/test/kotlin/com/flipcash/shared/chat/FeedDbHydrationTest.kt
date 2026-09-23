package com.flipcash.shared.chat

import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.user.UserManager
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.FeedSyncDelegate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chat list tells "loading" from "no chats" by whether the feed has emitted, so the feed must
 * not emit until it knows the answer: chats on disk, or the server's first reply. An empty list
 * emitted before that would put the empty state up over chats that are about to load from disk, or
 * on a fresh sign-in, over chats the first sync is about to write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedDbHydrationTest {

    private val rows = MutableSharedFlow<List<ChatMetadataEntity>>()
    private val stateHolder = ChatStateHolder()

    private val delegate = FeedSyncDelegate(
        chatController = mockk<ChatController>(relaxed = true),
        metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also {
            every { it.observeAll() } returns rows
        },
        messageDataSource = mockk<ChatMessageDataSource>(relaxed = true),
        memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also {
            every { it.observeAll() } returns flowOf(emptyMap<String, List<ChatMember>>())
        },
        stateHolder = stateHolder,
        userManager = mockk<UserManager>(relaxed = true).also {
            every { it.accountId } returns listOf<Byte>(1, 2, 3)
            every { it.profile } returns null
        },
    )

    @Test
    fun `feed does not emit before the database has been read`() = runTest {
        val emissions = mutableListOf<List<ChatSummary>>()
        delegate.feed(ChatType.TIP_DM, ChatType.GROUP)
            .onEach { emissions += it }
            .launchIn(backgroundScope)

        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()

        assertTrue(emissions.isEmpty(), "feed emitted $emissions before the database answered")
    }

    @Test
    fun `an empty database does not emit before the first sync answers`() = runTest {
        val emissions = collectFeed()

        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()
        rows.emit(emptyList())
        stateHolder.update { it.copy(feedSyncState = FeedSyncState.Syncing) }
        runCurrent()

        assertTrue(emissions.isEmpty(), "feed emitted $emissions before the sync answered")
    }

    @Test
    fun `an empty database emits an empty feed once the sync succeeds`() = runTest {
        val emissions = collectFeed()

        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()
        rows.emit(emptyList())
        stateHolder.update { it.copy(feedSyncState = FeedSyncState.Synced) }
        runCurrent()

        assertEquals(listOf(emptyList()), emissions)
    }

    @Test
    fun `an empty database emits an empty feed once the sync fails`() = runTest {
        val emissions = collectFeed()

        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()
        rows.emit(emptyList())
        stateHolder.update { it.copy(feedSyncState = FeedSyncState.Error) }
        runCurrent()

        assertEquals(listOf(emptyList()), emissions)
    }

    private fun TestScope.collectFeed(): List<List<ChatSummary>> {
        val emissions = mutableListOf<List<ChatSummary>>()
        delegate.feed(ChatType.TIP_DM, ChatType.GROUP)
            .onEach { emissions += it }
            .launchIn(backgroundScope)
        return emissions
    }
}
