package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.repository.ReactorsPage
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun id(byte: Int): ID = listOf(byte.toByte())

private fun reactor(userId: ID, reactedAt: Instant) = Reactor(userId = userId, reactedAt = reactedAt)

class ReactorsPrefetchCacheTest {

    @Test
    fun `start calls fetchPage for every emoji before the sheet would read rows`() = runTest {
        val calls = mutableListOf<Pair<Long, String>>()
        val cache = ReactorsPrefetchCache(scope = this) { messageId, emoji, _ ->
            calls += messageId to emoji
            Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))
        }

        // OpenReactors calls start() synchronously, one step before the navigator pushes the sheet
        // (see ChatViewModel's OpenReactors handler) — so by the time a sheet would compose and
        // read rows(), the fetch is already in flight.
        cache.start(messageId = 1L, emojis = listOf("👍", "🔥"))
        advanceUntilIdle()

        assertEquals(setOf(1L to "👍", 1L to "🔥"), calls.toSet())
    }

    @Test
    fun `rows publish once the fetch completes`() = runTest {
        val alice = id(2)
        val reactedAt = Instant.fromEpochSeconds(1_000)
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            Result.success(ReactorsPage(reactors = listOf(reactor(alice, reactedAt)), hasMore = false))
        }

        cache.start(messageId = 1L, emojis = listOf("👍"))
        advanceUntilIdle()

        assertEquals(false, cache.loading(1L).first())
        assertEquals(listOf(alice), cache.rows(1L).first().map { it.userId })
    }

    @Test
    fun `a rows subscriber that reads before start still sees the fetch complete`() = runTest {
        val alice = id(2)
        val reactedAt = Instant.fromEpochSeconds(1_000)
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            Result.success(ReactorsPage(reactors = listOf(reactor(alice, reactedAt)), hasMore = false))
        }

        // The sheet's own composition can read rows()/loading() before ChatViewModel's OpenReactors
        // handler has run start() — both derive from the same backing StateFlow, so a flow obtained
        // now still reflects the fetch once it lands, rather than being a stale placeholder.
        val rowsFlow = cache.rows(1L)

        cache.start(messageId = 1L, emojis = listOf("👍"))
        advanceUntilIdle()

        assertEquals(listOf(alice), rowsFlow.first().map { it.userId })
    }

    @Test
    fun `start is idempotent per messageId`() = runTest {
        var callCount = 0
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            callCount++
            Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))
        }

        cache.start(messageId = 1L, emojis = listOf("👍"))
        advanceUntilIdle()
        cache.start(messageId = 1L, emojis = listOf("👍"))
        advanceUntilIdle()

        assertEquals(1, callCount)
    }

    @Test
    fun `start no-ops for a message with no reactions`() = runTest {
        var callCount = 0
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            callCount++
            Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))
        }

        cache.start(messageId = 1L, emojis = emptyList())
        advanceUntilIdle()

        assertEquals(0, callCount)
        assertEquals(emptyList(), cache.rows(1L).first())
    }
}
