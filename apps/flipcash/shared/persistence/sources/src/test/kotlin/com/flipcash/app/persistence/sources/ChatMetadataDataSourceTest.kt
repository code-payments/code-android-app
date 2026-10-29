package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.dao.ChatMetadataDao
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.PinSummary
import com.flipcash.services.models.chat.ViewerState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.test.assertTrue

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
        try {
            every { FlipcashDatabase.getInstance() } returns mockk { every { chatMetadataDao() } returns dao }

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
                    version = 3,
                )
            }
        } finally {
            unmockkObject(FlipcashDatabase.Companion)
        }
    }

    private fun metadata(pins: PinSummary?) = ChatMetadata(
        chatId = ChatId(listOf(0xAB.toByte())),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(1),
        pinSummary = pins,
    )

    private fun withDao(block: suspend (ChatMetadataDao) -> Unit) = runTest {
        val dao = mockk<ChatMetadataDao>(relaxed = true)
        mockkObject(FlipcashDatabase.Companion)
        try {
            every { FlipcashDatabase.getInstance() } returns mockk { every { chatMetadataDao() } returns dao }
            block(dao)
        } finally {
            unmockkObject(FlipcashDatabase.Companion)
        }
    }

    private val chatId = ChatId(listOf(0xAB.toByte()))

    @Test
    fun `a feed upsert without a pin summary keeps the held one`() = withDao {
        val held = PinSummary(count = 1, version = 5)
        dataSource.upsert(metadata(held))

        dataSource.upsert(listOf(metadata(null)))
        assertEquals(held, dataSource.getPinSummary(chatId))

        dataSource.upsert(metadata(PinSummary(count = 0, version = 4)))
        assertEquals(held, dataSource.getPinSummary(chatId))
    }

    @Test
    fun `a GetChat write replaces the held summary with an equal or newer one`() = withDao { dao ->
        coEvery { dao.getById("ab") } returns null
        dataSource.upsert(metadata(PinSummary(count = 1, version = 5)))

        val newer = PinSummary(count = 2, version = 6)
        dataSource.upsertKeepingMembership(metadata(newer))

        assertEquals(newer, dataSource.getPinSummary(chatId))
    }

    @Test
    fun `metadata rebuilt from a row carries the held pin summary`() = withDao {
        val held = PinSummary(count = 1, version = 5)
        dataSource.upsert(metadata(held))
        val entity = ChatMetadataEntity(
            chatIdHex = "ab",
            chatType = ChatType.GROUP.name,
            lastActivityEpochMs = 1,
            lastMessageId = null,
        )

        assertEquals(held, dataSource.toMetadata(entity, emptyList(), null).pinSummary)
    }

    @Test
    fun `a pins event is dropped unless newer than what is held`() = withDao {
        val held = PinSummary(count = 1, version = 5)
        dataSource.upsert(metadata(held))

        dataSource.updatePinSummary(chatId, PinSummary(count = 0, version = 5))
        assertEquals(held, dataSource.getPinSummary(chatId))

        val newer = PinSummary(count = 0, version = 6)
        dataSource.updatePinSummary(chatId, newer)
        assertEquals(newer, dataSource.getPinSummary(chatId))
    }

    @Test
    fun `held pins do not carry over to another database`() = runTest {
        val first = mockk<FlipcashDatabase> { every { chatMetadataDao() } returns mockk(relaxed = true) }
        val second = mockk<FlipcashDatabase> { every { chatMetadataDao() } returns mockk(relaxed = true) }
        mockkObject(FlipcashDatabase.Companion)
        try {
            every { FlipcashDatabase.getInstance() } returns first
            dataSource.upsert(metadata(PinSummary(count = 1, version = 5)))

            every { FlipcashDatabase.getInstance() } returns second
            assertNull(dataSource.getPinSummary(chatId))
        } finally {
            unmockkObject(FlipcashDatabase.Companion)
        }
    }
}
