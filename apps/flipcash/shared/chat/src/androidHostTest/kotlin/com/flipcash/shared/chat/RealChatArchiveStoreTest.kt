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
import com.getcode.opencode.model.core.errors.ValidationException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
    fun `UNIMPLEMENTED keeps the write showing and sends nothing more until the next launch`() = runBlocking {
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        coEvery { chatController.archiveChat(any()) } returns Result.failure(ArchiveChatError.Unimplemented())

        store.archive(a)
        store.archive(b)
        store.flush()

        coVerify(exactly = 1) { chatController.archiveChat(any()) }
        assertTrue(store.isArchived(a), "the write still shows")
        assertTrue(store.isArchived(b), "a write made after the answer still shows")
        assertEquals(2, archiveDataSource.pending().size)
        assertFalse(marker.uploaded, "a queue the server did not take is not uploaded")

        // The next launch asks again.
        val relaunched = RealChatArchiveStore(archiveDataSource, metadata, chatController, marker) { 1_000L }
        coEvery { chatController.archiveChat(any()) } answers { Result.success(archived(2)) }
        relaunched.flush()

        assertTrue(archiveDataSource.pending().isEmpty())
        assertNotNull(metadata.archivedAt(a))
        assertNotNull(metadata.archivedAt(b))
        assertTrue(marker.uploaded)
    }

    @Test
    fun `an answered error keeps the write and the pass goes on to the next`() = runBlocking {
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)
        archiveDataSource.putPending(hex(b), archived = true, at = 2)
        coEvery { chatController.archiveChat(a) } returns
            Result.failure(ArchiveChatError.Other(RuntimeException("UNAUTHENTICATED")))
        coEvery { chatController.archiveChat(b) } returns Result.success(archived(2))

        store.flush()

        coVerify(exactly = 1) { chatController.archiveChat(b) }
        assertEquals(listOf(hex(a)), archiveDataSource.pending().map { it.chatIdHex })
        assertTrue(store.isArchived(a), "the refused write still shows")
        assertFalse(marker.uploaded, "a write still waiting means the upload is not done")
    }

    @Test
    fun `a transport failure keeps the write and ends the pass`() = runBlocking {
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)
        archiveDataSource.putPending(hex(b), archived = true, at = 2)
        coEvery { chatController.archiveChat(any()) } returns
            Result.failure(ArchiveChatError.Other(java.io.IOException("offline")))

        store.flush()

        coVerify(exactly = 1) { chatController.archiveChat(any()) }
        assertEquals(2, archiveDataSource.pending().size)
        assertFalse(marker.uploaded)
    }

    @Test
    fun `a send stays unsettled for the prune from before it goes until after its answer`() = runBlocking {
        seed(a, viewerState = inbox(1))
        val before = metadata.archiveMark()
        var unsettledDuringSend: Set<String>? = null
        coEvery { chatController.archiveChat(a) } answers {
            unsettledDuringSend = metadata.archiveUnsettledSince(before)
            Result.success(archived(2))
        }

        store.archive(a)

        assertEquals(setOf(hex(a)), unsettledDuringSend, "in flight while the call is out")
        assertEquals(setOf(hex(a)), metadata.archiveUnsettledSince(before), "moved since a pass that began before it")
        assertTrue(metadata.archiveUnsettledSince(metadata.archiveMark()).isEmpty(), "settled for a pass that begins after")
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

    // region Send order

    private val c = ChatId("55667788")

    /** [id]'s archive call completes [out] when it goes, and answers once [answer] completes. */
    private fun holdArchive(id: ChatId, out: CompletableDeferred<Unit>, answer: CompletableDeferred<Unit>) {
        coEvery { chatController.archiveChat(id) } coAnswers {
            out.complete(Unit)
            answer.await()
            Result.success(archived(2))
        }
    }

    private fun archiveGoes(id: ChatId, out: CompletableDeferred<Unit>) {
        coEvery { chatController.archiveChat(id) } coAnswers {
            out.complete(Unit)
            Result.success(archived(2))
        }
    }

    /** Sends one write on [c] that gets [result] back, before the write under test. */
    private suspend fun firstSendGets(result: Result<ViewerState>) {
        seed(c, viewerState = inbox(1))
        coEvery { chatController.archiveChat(c) } returns result
        store.archive(c)
    }

    /** Asserts [b]'s write waits for [a]'s send to be answered. */
    private suspend fun CoroutineScope.assertSecondWriteWaits() {
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        val aOut = CompletableDeferred<Unit>()
        val aAnswer = CompletableDeferred<Unit>()
        val bOut = CompletableDeferred<Unit>()
        holdArchive(a, aOut, aAnswer)
        archiveGoes(b, bOut)

        val first = launch { store.archive(a) }
        withTimeout(5_000) { aOut.await() }
        val second = launch { store.archive(b) }

        assertNull(withTimeoutOrNull(500) { bOut.await() }, "b goes only once a is answered")
        aAnswer.complete(Unit)
        withTimeout(5_000) { bOut.await() }
        first.join()
        second.join()
    }

    @Test
    fun `until a send is answered, the next write waits for it`() = runBlocking {
        assertSecondWriteWaits()
    }

    @Test
    fun `a transport failure is no answer, so writes still go one at a time`() = runBlocking {
        firstSendGets(Result.failure(ArchiveChatError.Other(java.io.IOException("offline"))))

        assertSecondWriteWaits()
    }

    @Test
    fun `once a send is answered, writes on different chats go at once`() = runBlocking {
        firstSendGets(Result.success(archived(2)))
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        val aOut = CompletableDeferred<Unit>()
        val aAnswer = CompletableDeferred<Unit>()
        val bOut = CompletableDeferred<Unit>()
        holdArchive(a, aOut, aAnswer)
        archiveGoes(b, bOut)

        val first = launch { store.archive(a) }
        withTimeout(5_000) { aOut.await() }
        val second = launch { store.archive(b) }

        withTimeout(5_000) { bOut.await() }
        assertFalse(aAnswer.isCompleted, "b went while a was still out")
        second.join()
        aAnswer.complete(Unit)
        first.join()
    }

    @Test
    fun `a chat's next write waits for its send already out`() = runBlocking {
        firstSendGets(Result.success(archived(2)))
        seed(a, viewerState = inbox(1))
        val aOut = CompletableDeferred<Unit>()
        val aAnswer = CompletableDeferred<Unit>()
        val unarchiveOut = CompletableDeferred<Unit>()
        holdArchive(a, aOut, aAnswer)
        coEvery { chatController.unarchiveChat(a) } coAnswers {
            unarchiveOut.complete(Unit)
            Result.success(UnarchivedChat(viewerState = inbox(3), chat = null))
        }

        val archiving = launch { store.archive(a) }
        withTimeout(5_000) { aOut.await() }
        val unarchiving = launch { store.unarchive(a) }

        assertNull(withTimeoutOrNull(500) { unarchiveOut.await() }, "the unarchive goes once the archive is answered")
        aAnswer.complete(Unit)
        withTimeout(5_000) { unarchiveOut.await() }
        archiving.join()
        unarchiving.join()
        assertFalse(store.isArchived(a))
    }

    @Test
    fun `a request that fails validation is no answer, so writes still go one at a time`() = runBlocking {
        firstSendGets(Result.failure(ValidationException(emptyList())))

        assertSecondWriteWaits()
    }

    @Test
    fun `until a send is answered, a flush sends one write at a time`() = runBlocking {
        marker.uploaded = true
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)
        archiveDataSource.putPending(hex(b), archived = true, at = 2)
        val aOut = CompletableDeferred<Unit>()
        val aAnswer = CompletableDeferred<Unit>()
        val bOut = CompletableDeferred<Unit>()
        holdArchive(a, aOut, aAnswer)
        archiveGoes(b, bOut)

        val flushing = launch { store.flush() }
        withTimeout(5_000) { aOut.await() }

        assertNull(withTimeoutOrNull(500) { bOut.await() }, "b goes only once a is answered")
        aAnswer.complete(Unit)
        withTimeout(5_000) { bOut.await() }
        flushing.join()
    }

    @Test
    fun `once a send is answered, a flush sends its writes at once`() = runBlocking {
        firstSendGets(Result.success(archived(2)))
        marker.uploaded = true
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)
        archiveDataSource.putPending(hex(b), archived = true, at = 2)
        val aOut = CompletableDeferred<Unit>()
        val aAnswer = CompletableDeferred<Unit>()
        val bOut = CompletableDeferred<Unit>()
        holdArchive(a, aOut, aAnswer)
        archiveGoes(b, bOut)

        val flushing = launch { store.flush() }
        withTimeout(5_000) { aOut.await() }

        withTimeout(5_000) { bOut.await() }
        assertFalse(aAnswer.isCompleted, "b went while a was still out")
        aAnswer.complete(Unit)
        flushing.join()
    }

    @Test
    fun `a flush does not send a write a tap replaced after the pass began`() = runBlocking {
        firstSendGets(Result.success(archived(2)))
        marker.uploaded = true
        seed(a, viewerState = inbox(1))
        seed(b, viewerState = inbox(1))
        archiveDataSource.putPending(hex(a), archived = true, at = 1)
        val aOut = CompletableDeferred<Unit>()
        val aAnswer = CompletableDeferred<Unit>()
        val bOut = CompletableDeferred<Unit>()
        val bAnswer = CompletableDeferred<Unit>()
        holdArchive(a, aOut, aAnswer)
        holdArchive(b, bOut, bAnswer)
        coEvery { chatController.unarchiveChat(b) } returns
            Result.success(UnarchivedChat(viewerState = inbox(3), chat = null))

        // A tap's archive of b is out, so the pass's send for b waits behind it.
        val archiving = launch { store.archive(b) }
        withTimeout(5_000) { bOut.await() }
        val flushing = launch { store.flush() }
        withTimeout(5_000) { aOut.await() }
        // The pass read b's archive; this unarchive replaces it before the archive is answered.
        val unarchiving = launch { store.unarchive(b) }
        withTimeout(5_000) { while (store.isArchived(b)) delay(10) }
        bAnswer.complete(Unit)
        aAnswer.complete(Unit)
        archiving.join()
        unarchiving.join()
        flushing.join()

        coVerify(exactly = 1) { chatController.archiveChat(b) }
        coVerify(exactly = 1) { chatController.unarchiveChat(b) }
        assertFalse(store.isArchived(b))
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
