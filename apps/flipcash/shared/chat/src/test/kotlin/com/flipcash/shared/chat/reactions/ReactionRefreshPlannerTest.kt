package com.flipcash.shared.chat.reactions

import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionRefreshPlannerTest {

    @Test
    fun `initial window caps at window size`() {
        val ids = (1L..100L).toList()
        val window = ReactionRefreshPlanner.initialWindow(ids, windowSize = 60)
        assertEquals(60, window.size)
        assertEquals(ids.take(60), window)
    }

    @Test
    fun `initial window under window size returns all`() {
        val ids = (1L..10L).toList()
        assertEquals(ids, ReactionRefreshPlanner.initialWindow(ids, windowSize = 60))
    }

    @Test
    fun `initial window excludes already refreshed ids`() {
        val ids = listOf(1L, 2L, 3L, 4L, 5L)
        val window = ReactionRefreshPlanner.initialWindow(ids, alreadyRefreshed = setOf(2L, 4L), windowSize = 60)
        assertEquals(listOf(1L, 3L, 5L), window)
    }

    @Test
    fun `loaded page from server is skipped entirely`() {
        val ids = (1L..100L).toList()
        val chunks = ReactionRefreshPlanner.forLoadedPage(ids, sourcedFromServer = true)
        assertEquals(emptyList<List<Long>>(), chunks)
    }

    @Test
    fun `loaded page from room is chunked`() {
        val ids = (1L..100L).toList()
        val chunks = ReactionRefreshPlanner.forLoadedPage(ids, sourcedFromServer = false, chunkSize = 40)
        assertEquals(3, chunks.size)
        assertEquals(40, chunks[0].size)
        assertEquals(40, chunks[1].size)
        assertEquals(20, chunks[2].size)
        assertEquals(ids, chunks.flatten())
    }

    @Test
    fun `loaded page excludes already refreshed and drops empty result`() {
        val ids = listOf(1L, 2L, 3L)
        val chunks = ReactionRefreshPlanner.forLoadedPage(
            ids,
            sourcedFromServer = false,
            alreadyRefreshed = setOf(1L, 2L, 3L),
        )
        assertEquals(emptyList<List<Long>>(), chunks)
    }

    @Test
    fun `loaded page empty input yields no chunks`() {
        val chunks = ReactionRefreshPlanner.forLoadedPage(emptyList(), sourcedFromServer = false)
        assertEquals(emptyList<List<Long>>(), chunks)
    }
}
