package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.repository.ReactorsPage
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        // (see ChatViewModel's OpenReactors handler) — so the fetch is in flight by the time a
        // sheet composes and reads rows(), rather than starting from the sheet's own LaunchedEffect.
        cache.start(messageId = 1L, emojis = listOf("👍", "🔥"))
        assertTrue(cache.loading(1L).value) // in flight immediately, before any dispatcher runs it

        advanceUntilIdle()

        assertEquals(setOf(1L to "👍", 1L to "🔥"), calls.toSet())
    }

    @Test
    fun `rows publish once the fetch completes`() = runTest {
        val alice = id(2)
        val reactedAt = Instant.fromEpochSeconds(1_000)
        val cache = ReactorsPrefetchCache(scope = this) { _, emoji, _ ->
            Result.success(ReactorsPage(reactors = listOf(reactor(alice, reactedAt)), hasMore = false))
        }

        cache.start(messageId = 1L, emojis = listOf("👍"))
        assertTrue(cache.loading(1L).value)

        advanceUntilIdle()

        assertFalse(cache.loading(1L).value)
        assertEquals(listOf(alice), cache.rows(1L).value.map { it.userId })
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
        assertEquals(emptyList(), cache.rows(1L).value)
    }
}

private fun assertFalse(actual: Boolean) = assertEquals(false, actual)
