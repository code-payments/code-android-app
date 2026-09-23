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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The chat list tells "loading" from "no chats" by whether the feed has emitted, so the feed must
 * not emit until the database has been read. An empty list emitted before that would put the empty
 * state up over chats that are about to load from disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedDbHydrationTest {

    private val rows = MutableSharedFlow<List<ChatMetadataEntity>>()

    private val delegate = FeedSyncDelegate(
        chatController = mockk<ChatController>(relaxed = true),
        metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true).also {
            every { it.observeAll() } returns rows
        },
        messageDataSource = mockk<ChatMessageDataSource>(relaxed = true),
        memberDataSource = mockk<ChatMemberDataSource>(relaxed = true).also {
            every { it.observeAll() } returns flowOf(emptyMap<String, List<ChatMember>>())
        },
        stateHolder = ChatStateHolder(),
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
    fun `an empty database still emits an empty feed`() = runTest {
        val emissions = mutableListOf<List<ChatSummary>>()
        delegate.feed(ChatType.TIP_DM, ChatType.GROUP)
            .onEach { emissions += it }
            .launchIn(backgroundScope)

        delegate.initialize(backgroundScope)
        delegate.observeFeedFromDb()
        runCurrent()
        rows.emit(emptyList())
        runCurrent()

        assertEquals(listOf(emptyList()), emissions)
    }
}
