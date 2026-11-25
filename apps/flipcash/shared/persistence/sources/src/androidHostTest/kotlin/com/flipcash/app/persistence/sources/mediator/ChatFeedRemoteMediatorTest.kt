package com.flipcash.app.persistence.sources.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatFolder
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The merged feed's paging contract. Its job is to advance one source at a time — the one the
 * merged list is waiting on — and to hold the removal reconciliation back until the whole feed
 * has been seen, since a chat absent from a partial pass has not been left, only not reached.
 */
@OptIn(ExperimentalPagingApi::class)
class ChatFeedRemoteMediatorTest {

    private val controller = mockk<ChatController>()
    private val metadataDataSource = mockk<ChatMetadataDataSource>(relaxed = true)
    private val memberDataSource = mockk<ChatMemberDataSource>(relaxed = true)
    private val messageDataSource = mockk<ChatMessageDataSource>(relaxed = true)

    private fun mediator() = ChatFeedRemoteMediator(
        chatTypes = listOf(ChatType.CONTACT_DM, ChatType.GROUP),
        controller = controller,
        metadataDataSource = metadataDataSource,
        memberDataSource = memberDataSource,
        messageDataSource = messageDataSource,
    )

    private fun chat(hex: String, type: ChatType, lastActivityMs: Long) = ChatMetadata(
        chatId = ChatId(hex),
        type = type,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochMilliseconds(lastActivityMs),
    )

    private fun page(vararg chats: ChatMetadata, hasMore: Boolean) =
        ChatFeedPage(chats = chats.toList(), pagingToken = null, hasMore = hasMore)

    private val state = PagingState<Int, ChatMetadataEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0,
    )

    private fun dmReturns(vararg pages: ChatFeedPage) {
        coEvery { controller.getDmChatFeed(ChatType.CONTACT_DM, any()) } returnsMany
            pages.map { Result.success(it) }
    }

    private fun groupReturns(vararg pages: ChatFeedPage) {
        coEvery { controller.getGroupChatFeed(any(), ChatFolder.INBOX) } returnsMany
            pages.map { Result.success(it) }
    }

    private fun archivedGroupsReturn(vararg pages: ChatFeedPage) {
        coEvery { controller.getGroupChatFeed(any(), ChatFolder.ARCHIVED) } returnsMany
            pages.map { Result.success(it) }
    }

    private fun archivedDmsReturn(vararg pages: ChatFeedPage) {
        coEvery { controller.getDmChatFeed(ChatType.CONTACT_DM, any(), ChatFolder.ARCHIVED) } returnsMany
            pages.map { Result.success(it) }
    }

    init {
        // Nothing archived unless a test says otherwise; a complete pass always reads the folder.
        archivedGroupsReturn(page(hasMore = false))
        archivedDmsReturn(page(hasMore = false))
        every { metadataDataSource.archiveMark() } returns 0L
        every { metadataDataSource.archiveUnsettledSince(any()) } returns emptySet()
    }

    @Test
    fun `a refresh pages every source`() = runTest {
        dmReturns(page(chat("01", ChatType.CONTACT_DM, 500), hasMore = true))
        groupReturns(page(chat("02", ChatType.GROUP, 100), hasMore = true))

        val result = mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 1) { controller.getDmChatFeed(ChatType.CONTACT_DM, any()) }
        coVerify(exactly = 1) { controller.getGroupChatFeed(any()) }
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun `a refresh persists what it fetched`() = runTest {
        val dm = chat("01", ChatType.CONTACT_DM, 500)
        dmReturns(page(dm, hasMore = false))
        groupReturns(page(hasMore = false))

        mediator().load(LoadType.REFRESH, state)

        coVerify { metadataDataSource.upsert(listOf(dm)) }
        coVerify { memberDataSource.upsert(dm.chatId, emptyList()) }
    }

    @Test
    fun `an append advances only the source the list is waiting on`() = runTest {
        dmReturns(
            page(chat("01", ChatType.CONTACT_DM, 500), hasMore = true),
            page(chat("03", ChatType.CONTACT_DM, 50), hasMore = true),
        )
        groupReturns(page(chat("02", ChatType.GROUP, 100), hasMore = true))

        val subject = mediator()
        subject.load(LoadType.REFRESH, state)
        subject.load(LoadType.APPEND, state)

        // The group feed has already reached 100 and the DM feed stopped at 500, so the DM feed
        // is the one with unknown items in between.
        coVerify(exactly = 2) { controller.getDmChatFeed(ChatType.CONTACT_DM, any()) }
        coVerify(exactly = 1) { controller.getGroupChatFeed(any()) }
    }

    @Test
    fun `pagination ends only once every source has run out`() = runTest {
        dmReturns(page(chat("01", ChatType.CONTACT_DM, 500), hasMore = false))
        groupReturns(
            page(chat("02", ChatType.GROUP, 100), hasMore = true),
            page(chat("03", ChatType.GROUP, 50), hasMore = false),
        )

        val subject = mediator()
        val refresh = subject.load(LoadType.REFRESH, state)
        assertEquals(false, (refresh as RemoteMediator.MediatorResult.Success).endOfPaginationReached)

        val append = subject.load(LoadType.APPEND, state)
        assertEquals(true, (append as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun `a prepend has nothing to do`() = runTest {
        val result = mediator().load(LoadType.PREPEND, state)

        assertEquals(true, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    /**
     * Replaces the old asymmetry, where a CONTACT_DM failure failed the whole sync and a TIP_DM
     * failure was swallowed. Every source now fails on its own terms.
     */
    @Test
    fun `one source failing does not stop the others`() = runTest {
        val dm = chat("01", ChatType.CONTACT_DM, 500)
        dmReturns(page(dm, hasMore = false))
        coEvery { controller.getGroupChatFeed(any()) } returns Result.failure(Throwable("unreachable"))

        val result = mediator().load(LoadType.REFRESH, state)

        coVerify { metadataDataSource.upsert(listOf(dm)) }
        assertEquals(false, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun `an append with only failed sources left surfaces the failure`() = runTest {
        dmReturns(page(chat("01", ChatType.CONTACT_DM, 500), hasMore = false))
        coEvery { controller.getGroupChatFeed(any()) } returns Result.failure(Throwable("unreachable"))

        val subject = mediator()
        subject.load(LoadType.REFRESH, state)
        val result = subject.load(LoadType.APPEND, state)

        assertTrue(result is RemoteMediator.MediatorResult.Error)
    }

    @Test
    fun `a group missing from a complete pass loses its membership`() = runTest {
        val stillThere = chat("01", ChatType.GROUP, 500)
        dmReturns(page(hasMore = false))
        groupReturns(page(stillThere, hasMore = false))
        coEvery { metadataDataSource.chatIdHex(stillThere.chatId) } returns "01"
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns listOf("01", "02")
        coEvery { metadataDataSource.removeFromChat(any<String>()) } just Runs

        mediator().load(LoadType.REFRESH, state)

        coVerify { metadataDataSource.removeFromChat("02") }
        coVerify(exactly = 0) { metadataDataSource.removeFromChat("01") }
    }

    /**
     * A chat below the point the pass reached has not been left — it has not been looked at.
     * Reconciling on a partial pass would clear the membership of every conversation past the
     * first page.
     */
    @Test
    fun `an incomplete pass reconciles nothing`() = runTest {
        dmReturns(page(hasMore = true))
        groupReturns(page(chat("01", ChatType.GROUP, 500), hasMore = true))
        coEvery { metadataDataSource.chatIdHex(any()) } returns "01"

        mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 0) { metadataDataSource.getChatIdsOfType(any()) }
        coVerify(exactly = 0) { metadataDataSource.removeFromChat(any<String>()) }
        coVerify(exactly = 0) { controller.getGroupChatFeed(any(), ChatFolder.ARCHIVED) }
    }

    /**
     * The pass pages the inbox, which leaves archived groups out. "02" was archived on another
     * device while this one was offline, so nothing here marks it archived yet: only the
     * archived read keeps its membership.
     */
    @Test
    fun `a group archived elsewhere keeps its membership through a complete pass`() = runTest {
        val inInbox = chat("01", ChatType.GROUP, 500)
        val archived = chat("02", ChatType.GROUP, 400)
        dmReturns(page(hasMore = false))
        groupReturns(page(inInbox, hasMore = false))
        archivedGroupsReturn(page(archived, hasMore = false))
        coEvery { metadataDataSource.chatIdHex(inInbox.chatId) } returns "01"
        coEvery { metadataDataSource.chatIdHex(archived.chatId) } returns "02"
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns listOf("01", "02", "03")
        coEvery { metadataDataSource.removeFromChat(any<String>()) } just Runs

        mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 1) { metadataDataSource.removeFromChat(any<String>()) }
        coVerify { metadataDataSource.removeFromChat("03") }
        coVerify { metadataDataSource.upsert(listOf(archived)) }
    }

    @Test
    fun `the archived read pages to the end before the prune`() = runTest {
        val first = chat("02", ChatType.GROUP, 400)
        val second = chat("03", ChatType.GROUP, 300)
        dmReturns(page(hasMore = false))
        groupReturns(page(hasMore = false))
        archivedGroupsReturn(
            ChatFeedPage(chats = listOf(first), pagingToken = listOf(1.toByte()), hasMore = true),
            page(second, hasMore = false),
        )
        coEvery { metadataDataSource.chatIdHex(first.chatId) } returns "02"
        coEvery { metadataDataSource.chatIdHex(second.chatId) } returns "03"
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns listOf("02", "03")

        mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 2) { controller.getGroupChatFeed(any(), ChatFolder.ARCHIVED) }
        coVerify(exactly = 0) { metadataDataSource.removeFromChat(any<String>()) }
    }

    @Test
    fun `a failed archived read holds the prune back`() = runTest {
        dmReturns(page(hasMore = false))
        groupReturns(page(hasMore = false))
        coEvery { controller.getGroupChatFeed(any(), ChatFolder.ARCHIVED) } returns
            Result.failure(Throwable("unreachable"))
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns listOf("02")

        val result = mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 0) { metadataDataSource.removeFromChat(any<String>()) }
        assertEquals(true, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun `an archived page that claims more with no token holds the prune back`() = runTest {
        dmReturns(page(hasMore = false))
        groupReturns(page(hasMore = false))
        archivedGroupsReturn(page(hasMore = true))
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns listOf("02")

        mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 1) { controller.getGroupChatFeed(any(), ChatFolder.ARCHIVED) }
        coVerify(exactly = 0) { metadataDataSource.removeFromChat(any<String>()) }
    }

    @Test
    fun `archived DMs are read to the end on a complete pass`() = runTest {
        val first = chat("11", ChatType.CONTACT_DM, 400)
        val second = chat("12", ChatType.CONTACT_DM, 300)
        dmReturns(page(hasMore = false))
        groupReturns(page(hasMore = false))
        archivedDmsReturn(
            ChatFeedPage(chats = listOf(first), pagingToken = listOf(1.toByte()), hasMore = true),
            page(second, hasMore = false),
        )

        mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 2) { controller.getDmChatFeed(ChatType.CONTACT_DM, any(), ChatFolder.ARCHIVED) }
        coVerify { metadataDataSource.upsert(listOf(first, second)) }
    }

    @Test
    fun `a failed archived DM read holds the group prune back`() = runTest {
        dmReturns(page(hasMore = false))
        groupReturns(page(hasMore = false))
        coEvery { controller.getDmChatFeed(ChatType.CONTACT_DM, any(), ChatFolder.ARCHIVED) } returns
            Result.failure(Throwable("unreachable"))
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns listOf("02")

        mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 0) { metadataDataSource.removeFromChat(any<String>()) }
    }

    /**
     * "02" moved between the folders while the reads ran, so the inbox read passed it before the
     * move and the archived read came after it: it is in neither. Its write being in flight, or
     * its archive having moved since the pass began, is what keeps it.
     */
    @Test
    fun `a group whose archive is unsettled keeps its membership`() = runTest {
        dmReturns(page(hasMore = false))
        groupReturns(page(hasMore = false))
        every { metadataDataSource.archiveUnsettledSince(any()) } returns setOf("02")
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns listOf("02", "03")
        coEvery { metadataDataSource.removeFromChat(any<String>()) } just Runs

        mediator().load(LoadType.REFRESH, state)

        coVerify(exactly = 1) { metadataDataSource.removeFromChat(any<String>()) }
        coVerify { metadataDataSource.removeFromChat("03") }
    }

    @Test
    fun `the prune judges moves against the mark taken when the refresh began`() = runTest {
        dmReturns(page(hasMore = false))
        groupReturns(page(hasMore = false))
        // Construction reads 0; the refresh reads 7 before its first fetch.
        every { metadataDataSource.archiveMark() } returnsMany listOf(0L, 7L)
        coEvery { metadataDataSource.getChatIdsOfType(ChatType.GROUP) } returns emptyList()

        mediator().load(LoadType.REFRESH, state)

        verify { metadataDataSource.archiveUnsettledSince(7L) }
    }
}
