package com.flipcash.app.persistence.sources

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * No Robolectric here, so only the no-database paths run; the read/write paths are covered over a
 * real database by the archive store's test.
 */
class ChatArchiveDataSourceTest {

    private val source = ChatArchiveDataSource()

    @Test
    fun `with no database observeIds is an empty set, not an empty flow`() = runTest {
        // An empty *flow* would stall a combine() that includes it; an empty *set* lets it emit.
        assertEquals(emptySet(), source.observeIds().first())
    }

    @Test
    fun `with no database nothing is archived and writes are no-ops`() = runTest {
        source.archive("aa", now = 1L)
        source.unarchive("aa")
        source.clear()
        assertFalse(source.isArchived("aa"))
    }
}
