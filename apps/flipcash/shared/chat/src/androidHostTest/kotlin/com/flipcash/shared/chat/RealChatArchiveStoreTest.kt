package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.init
import com.flipcash.app.persistence.sources.ChatArchiveDataSource
import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.ArchiveChatError
import com.flipcash.services.models.UnarchiveChatError
import com.flipcash.services.models.chat.ArchiveState
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.UnarchivedChat
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.shared.chat.internal.ArchiveUploadMarker
import com.flipcash.shared.chat.internal.RealChatArchiveStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The store over a real database: writes show at once, and the server's answer to each decides
 * whether it is applied, dropped or kept for the next flush.
 */
@RunWith(RobolectricTestRunner::class)
class RealChatArchiveStoreTest {

    private val a = ChatId("aabbccdd")
    private val b = ChatId("11223344")

    private val archiveDataSource = ChatArchiveDataSource()
    private val metadata = ChatMetadataDataSource(ChatEntityMapper())
    private val chatController = mockk<ChatController>()
    private val marker = FakeMarker()
    private val store = RealChatArchiveStore(archiveDataSource, metadata, chatController, marker) { 1_000L }

    private class FakeMarker(var uploaded: Boolean = false) : ArchiveUploadMarker {
        override suspend fun isSet() = uploaded
        override suspend fun set() {
            uploaded = true
        }
    }

    @Before
    fun setUp() {
        FlipcashDatabase.init(ApplicationProvider.getApplicationContext<Context>(), "chat-archive-store-test")
        runBlocking { store.clearAll() }
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    private fun archived(version: Long) =
        ViewerState(archive = ArchiveState(Instant.fromEpochMilliseconds(5_000)), version = version)

    private fun inbox(version: Long) = ViewerState(version = version)

    private fun chat(id: ChatId, type: ChatType, viewerState: ViewerState? = null) = ChatMetadata(
        chatId = id,
        type = type,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1_000),
        viewerState = viewerState,
    )

    private suspend fun seed(id: ChatId, type: ChatType = ChatType.CONTACT_DM, viewerState: ViewerState? = null) =
        metadata.upsert(chat(id, type, viewerState))

    // region Upload outcomes

    @Test
    fun `OK applies the returned viewer state and retires the write`() = runBlocking {
        seed(a, viewerState = inbox(1))
        coEvery { chatController.archiveChat(a) } returns Result.success(archived(2))

        store.archive(a)

        assertTrue(store.isArchived(a))
        assertNotNull(metadata.archivedAt(a), "the server's archive is stored")
        assertTrue(archiveDataSource.pending().isEmpty())
    }

    @Test
    fun `DENIED drops the write and the chat stays in the inbox`() = runBlocking {
        seed(a, viewerState = inbox(1))
        coEvery { chatController.archiveChat(a) } returns Result.failure(ArchiveChatError.Denied())

        store.archive(a)

        assertFalse(store.isArchived(a))
        assertTrue(archiveDataSource.pending().isEmpty())
    }

    @Test
    fun `NOT_FOUND drops the write`() = runBlocking {
        seed(a, viewerState = inbox(1))
        coEvery { chatController.archiveChat(a) } returns Result.failure(ArchiveChatError.NotFound())

        store.archive(a)

        assertFalse(store.isArchived(a))
        assertTrue(archiveDataSource.pending().isEmpty())
    }

    @Test
    fun `UNIMPLEMENTED keeps the write showing and a later flush sends it`() = runBlocking {
        seed(a, viewerState = inbox(1))
        coEvery { chatController.archiveChat(a) } returns Result.failure(ArchiveChatError.Unimplemented())

        store.archive(a)

        assertTrue(store.isArchived(a), "the write still shows")
        assertEquals(1, archiveDataSource.pending().size)
        assertFalse(marker.uploaded, "a queue the server did not answer is not uploaded")

        coEvery { chatController.archiveChat(a) } returns Result.success(archived(2))
        store.flush()

        assertTrue(archiveDataSource.pending().isEmpty())
        assertNotNull(metadata.archivedAt(a))
        assertTrue(marker.uploaded)
    }

    @Test
    fun `UNIMPLEMENTED ends the pass so the rest of the queue is not sent`() = runBlocking {
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)
        archiveDataSource.putPending(hex(b), archived = true, at = 2)
        coEvery { chatController.archiveChat(any()) } returns Result.failure(ArchiveChatError.Unimplemented())

        store.flush()

        coVerify(exactly = 1) { chatController.archiveChat(any()) }
        assertEquals(2, archiveDataSource.pending().size)
    }

    @Test
    fun `a failed unarchive keeps the chat out of the archive until the server answers`() = runBlocking {
        seed(a, viewerState = archived(1))
        coEvery { chatController.unarchiveChat(a) } returns Result.failure(UnarchiveChatError.Other())

        store.unarchive(a)

        assertFalse(store.isArchived(a), "the pending unarchive wins over the server's archive")
        assertEquals(1, archiveDataSource.pending().size)
    }

    // endregion

    // region Version order

    @Test
    fun `an answer older than the stored state does not overwrite it`() = runBlocking {
        // The stream already delivered a re-archive at 7 before the unarchive's answer at 6.
        seed(a, ChatType.GROUP, viewerState = archived(7))
        archiveDataSource.putPending(hex(a), archived = false, at = 1)
        coEvery { chatController.unarchiveChat(a) } returns
            Result.success(UnarchivedChat(viewerState = inbox(6), chat = null))

        store.flush()

        assertTrue(archiveDataSource.pending().isEmpty())
        assertTrue(store.isArchived(a), "version 7 stands")
    }

    // endregion

    // region Unarchive catch-up

    @Test
    fun `unarchiving a group asks for its catch-up`() = runBlocking {
        seed(a, ChatType.GROUP, viewerState = archived(1))
        coEvery { chatController.unarchiveChat(a) } returns Result.success(
            UnarchivedChat(viewerState = inbox(2), chat = chat(a, ChatType.GROUP, inbox(2))),
        )
        val next = async(start = CoroutineStart.UNDISPATCHED) { store.unarchivedGroups().first() }

        store.unarchive(a)

        assertEquals(a, withTimeout(1_000) { next.await() })
        assertNull(metadata.archivedAt(a))
    }

    @Test
    fun `unarchiving a DM asks for no catch-up`() = runBlocking {
        seed(a, ChatType.CONTACT_DM, viewerState = archived(1))
        coEvery { chatController.unarchiveChat(a) } returns
            Result.success(UnarchivedChat(viewerState = inbox(2), chat = null))
        val seen = mutableListOf<ChatId>()
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) { store.unarchivedGroups().toList(seen) }

        store.unarchive(a)

        collecting.cancel()
        assertTrue(seen.isEmpty())
        assertFalse(store.isArchived(a))
    }

    // endregion

    // region One-time upload

    @Test
    fun `the first upload skips a record the server already holds`() = runBlocking {
        seed(a, viewerState = archived(3))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)

        store.flush()

        coVerify(exactly = 0) { chatController.archiveChat(any()) }
        assertTrue(archiveDataSource.pending().isEmpty())
        assertTrue(marker.uploaded)
    }

    @Test
    fun `after the upload a queued archive is sent even if the server shows it archived`() = runBlocking {
        marker.uploaded = true
        seed(a, viewerState = archived(3))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)
        coEvery { chatController.archiveChat(a) } returns Result.success(archived(3))

        store.flush()

        coVerify(exactly = 1) { chatController.archiveChat(a) }
    }

    // endregion

    @Test
    fun `archiving an archived chat sends nothing`() = runBlocking {
        seed(a, viewerState = archived(1))

        store.archive(a)

        coVerify(exactly = 0) { chatController.archiveChat(any()) }
    }

    @Test
    fun `forget drops a queued write without telling the server`() = runBlocking {
        seed(a, viewerState = inbox(1))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)

        store.forget(a)

        assertTrue(archiveDataSource.pending().isEmpty())
        assertFalse(store.isArchived(a))
    }

    @Test
    fun `None archives nothing`() = runBlocking {
        assertEquals(emptySet(), ChatArchiveStore.None.observeArchived().first())
        assertFalse(ChatArchiveStore.None.isArchived(a))
    }

    private fun hex(id: ChatId) = ChatEntityMapper().chatIdHex(id)
}
