package com.flipcash.app.persistence.sources

import androidx.paging.PagingSource
import com.flipcash.app.persistence.entities.ChatMetadataEntity
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatType
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
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
}
