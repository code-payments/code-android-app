package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.dao.ChatMetadataDao
import com.flipcash.app.persistence.inTransaction
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ArchiveState
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ViewerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The per-user database is opened at login, so every read here can be attempted before it
 * exists. The established answer is a null-safe fallback per method; a paged read is the one
 * case where the fallback cannot be an empty result — Paging would render "no conversations"
 * and never ask again.
 */
class ChatMetadataDataSourceTest {

    private val dataSource = ChatMetadataDataSource(ChatEntityMapper())

    @Test
    fun `the paged feed reports an error rather than an empty list when the database is closed`() = runTest {
        val result = dataSource.observeFeedPaged(listOf(ChatType.GROUP)).load(
            PagingSource.LoadParams.Refresh<Int>(null, 10, false)
        )

        assertTrue(result is PagingSource.LoadResult.Error<Int, ChatMetadataEntity>)
    }

    @Test
    fun `reads that have a meaningful empty answer keep returning one`() = runTest {
        assertEquals(emptyList(), dataSource.getChatIdsOfType(ChatType.GROUP))
        assertEquals(0L, dataSource.getRosterVersion(ChatId(listOf(0xAB.toByte()))))
    }

    @Test
    fun `a chat id renders as the hex the tables are keyed by`() {
        assertEquals("ab", dataSource.chatIdHex(ChatId(listOf(0xAB.toByte()))))
    }

    @Test
    fun `the invite grant is stored with the rest of the viewer state`() = runTest {
        val dao = mockk<ChatMetadataDao>(relaxed = true)
        mockkObject(FlipcashDatabase.Companion)
        // The write runs in a transaction, which a mocked database cannot open: run the block.
        mockkStatic(TRANSACTIONS)
        try {
            val db = mockk<FlipcashDatabase> { every { chatMetadataDao() } returns dao }
            coEvery { db.inTransaction(any<suspend () -> Any?>()) } coAnswers {
                secondArg<suspend () -> Any?>().invoke()
            }
            every { FlipcashDatabase.getInstance() } returns db

            dataSource.updateViewerState(
                ChatId(listOf(0xAB.toByte())),
                ViewerState(version = 3, permissions = ViewerState.Permissions(canInvite = true)),
            )

            coVerify {
                dao.updateViewerStateIfNewer(
                    chatIdHex = "ab",
                    muteUntilEpochMs = any(),
                    muteForever = false,
                    canEdit = false,
                    canInvite = true,
                    version = 3, archivedAtEpochMs = null,
                )
            }
        } finally {
            unmockkStatic(TRANSACTIONS)
            unmockkObject(FlipcashDatabase.Companion)
        }
    }

    @Test
    fun `a send leaves its chat unsettled while in flight and past every earlier mark after`() {
        val chatId = ChatId(listOf(0xAB.toByte()))
        val before = dataSource.archiveMark()

        dataSource.beginArchiveSend(chatId)
        assertTrue("ab" in dataSource.archiveUnsettledSince(dataSource.archiveMark()))

        dataSource.endArchiveSend(chatId)
        assertTrue("ab" in dataSource.archiveUnsettledSince(before))
        assertFalse("ab" in dataSource.archiveUnsettledSince(dataSource.archiveMark()))
    }

    @Test
    fun `overlapping sends keep the chat in flight until the last comes back`() {
        val chatId = ChatId(listOf(0xAB.toByte()))
        dataSource.beginArchiveSend(chatId)
        dataSource.beginArchiveSend(chatId)

        dataSource.endArchiveSend(chatId)
        assertTrue("ab" in dataSource.archiveUnsettledSince(dataSource.archiveMark()))

        dataSource.endArchiveSend(chatId)
        assertFalse("ab" in dataSource.archiveUnsettledSince(dataSource.archiveMark()))
    }

    @Test
    fun `a landed flip marks the chat moved, and a dropped write or a same-side write does not`() = runTest {
        val dao = mockk<ChatMetadataDao>(relaxed = true)
        mockkObject(FlipcashDatabase.Companion)
        mockkStatic(TRANSACTIONS)
        try {
            val db = mockk<FlipcashDatabase> { every { chatMetadataDao() } returns dao }
            coEvery { db.inTransaction(any<suspend () -> Any?>()) } coAnswers {
                secondArg<suspend () -> Any?>().invoke()
            }
            every { FlipcashDatabase.getInstance() } returns db
            val chatId = ChatId(listOf(0xAB.toByte()))
            val archived = ViewerState(version = 4, archive = ArchiveState(Instant.fromEpochMilliseconds(9)))
            coEvery { dao.getArchivedAt("ab") } returns null

            // The gate dropped it: nothing moved.
            coEvery { dao.updateViewerStateIfNewer(chatIdHex = "ab", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 0
            var mark = dataSource.archiveMark()
            assertEquals(ChatMetadataDataSource.ViewerStateWrite.Dropped, dataSource.updateViewerState(chatId, archived))
            assertFalse("ab" in dataSource.archiveUnsettledSince(mark))

            // Landed, and still out of the archive: a mute change, not a move.
            coEvery { dao.updateViewerStateIfNewer(chatIdHex = "ab", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
            mark = dataSource.archiveMark()
            assertEquals(ChatMetadataDataSource.ViewerStateWrite.Applied, dataSource.updateViewerState(chatId, ViewerState(version = 5)))
            assertFalse("ab" in dataSource.archiveUnsettledSince(mark))

            // Landed, and into the archive: moved.
            mark = dataSource.archiveMark()
            assertEquals(ChatMetadataDataSource.ViewerStateWrite.Archived, dataSource.updateViewerState(chatId, archived))
            assertTrue("ab" in dataSource.archiveUnsettledSince(mark))
        } finally {
            unmockkStatic(TRANSACTIONS)
            unmockkObject(FlipcashDatabase.Companion)
        }
    }

    private companion object {
        const val TRANSACTIONS = "com.flipcash.app.persistence.Transactions_androidKt"
    }
}
