package com.flipcash.shared.chat.reactions

import com.flipcash.services.models.PagingToken
import com.flipcash.services.models.chat.Reactor
import com.flipcash.services.repository.ReactorsPage
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun id(byte: Int): ID = listOf(byte.toByte())

private fun reactor(userId: ID, reactedAt: Instant) = Reactor(userId = userId, reactedAt = reactedAt)

private fun pill(emoji: String, count: Long = 1) =
    ReactionPill(emoji = emoji, count = count, selfReacted = false, pending = false)

class ReactorsPrefetchCacheTest {

    @Test
    fun `start calls fetchPage for every emoji before the sheet would read rows`() = runTest {
        val calls = mutableListOf<Pair<Long, String>>()
        val cache = ReactorsPrefetchCache(scope = this) { messageId, emoji, _ ->
            calls += messageId to emoji
            Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))
        }

        // start() alone puts the fetch in flight, before anything reads rows().
        cache.start(messageId = 1L, pills = listOf(pill("👍"), pill("🔥")))
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

        cache.start(messageId = 1L, pills = listOf(pill("👍")))
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

        // A collector can read rows()/loading() before start() has run — both derive from the same backing StateFlow, so a flow obtained
        // now still reflects the fetch once it lands, rather than being a stale placeholder.
        val rowsFlow = cache.rows(1L)

        cache.start(messageId = 1L, pills = listOf(pill("👍")))
        advanceUntilIdle()

        assertEquals(listOf(alice), rowsFlow.first().map { it.userId })
    }

    @Test
    fun `start is idempotent while the pills are unchanged`() = runTest {
        var callCount = 0
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            callCount++
            Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))
        }

        cache.start(messageId = 1L, pills = listOf(pill("👍")))
        advanceUntilIdle()
        cache.start(messageId = 1L, pills = listOf(pill("👍")))
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

        cache.start(messageId = 1L, pills = emptyList())
        advanceUntilIdle()

        assertEquals(0, callCount)
        assertEquals(emptyList(), cache.rows(1L).first())
    }

    @Test
    fun `start refetches when a pill's count changes and republishes the new reactors`() = runTest {
        val alice = id(2)
        val bob = id(3)
        var reactors = listOf(reactor(alice, Instant.fromEpochSeconds(1_000)))
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            Result.success(ReactorsPage(reactors = reactors, hasMore = false))
        }

        cache.start(messageId = 1L, pills = listOf(pill("👍", count = 1)))
        advanceUntilIdle()

        reactors = reactors + reactor(bob, Instant.fromEpochSeconds(2_000))
        cache.start(messageId = 1L, pills = listOf(pill("👍", count = 2)))
        advanceUntilIdle()

        assertEquals(listOf(bob, alice), cache.rows(1L).first().map { it.userId })
    }

    @Test
    fun `start fetches an emoji added since the first open`() = runTest {
        val calls = mutableListOf<String>()
        val cache = ReactorsPrefetchCache(scope = this) { _, emoji, _ ->
            calls += emoji
            Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))
        }

        cache.start(messageId = 1L, pills = listOf(pill("👍")))
        advanceUntilIdle()
        cache.start(messageId = 1L, pills = listOf(pill("👍"), pill("🔥")))
        advanceUntilIdle()

        assertEquals(listOf("👍", "👍", "🔥"), calls)
    }

    @Test
    fun `a refetch keeps the previous rows until its own round lands`() = runTest {
        val alice = id(2)
        val gate = CompletableDeferred<Unit>()
        var gated = false
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            if (gated) gate.await()
            Result.success(ReactorsPage(reactors = listOf(reactor(alice, Instant.fromEpochSeconds(1_000))), hasMore = false))
        }

        cache.start(messageId = 1L, pills = listOf(pill("👍", count = 1)))
        advanceUntilIdle()

        gated = true
        cache.start(messageId = 1L, pills = listOf(pill("👍", count = 2)))
        advanceUntilIdle()

        // Old rows stay up (no skeleton flash) while the refetch is in flight.
        assertEquals(listOf(alice), cache.rows(1L).first().map { it.userId })
        assertEquals(true, cache.loading(1L).first())

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, cache.loading(1L).first())
    }

    @Test
    fun `a superseded round does not overwrite the newer one`() = runTest {
        val alice = id(2)
        val bob = id(3)
        val firstGate = CompletableDeferred<Unit>()
        var round = 0
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            round++
            if (round == 1) {
                firstGate.await()
                Result.success(ReactorsPage(reactors = listOf(reactor(alice, Instant.fromEpochSeconds(1_000))), hasMore = false))
            } else {
                Result.success(ReactorsPage(reactors = listOf(reactor(bob, Instant.fromEpochSeconds(2_000))), hasMore = false))
            }
        }

        cache.start(messageId = 1L, pills = listOf(pill("👍", count = 1)))
        advanceUntilIdle()
        cache.start(messageId = 1L, pills = listOf(pill("👍", count = 2)))
        advanceUntilIdle()
        firstGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(bob), cache.rows(1L).first().map { it.userId })
        assertEquals(false, cache.loading(1L).first())
    }

    @Test
    fun `start with no pills drops the rows of a message whose reactions were all removed`() = runTest {
        val alice = id(2)
        val cache = ReactorsPrefetchCache(scope = this) { _, _, _ ->
            Result.success(ReactorsPage(reactors = listOf(reactor(alice, Instant.fromEpochSeconds(1_000))), hasMore = false))
        }

        cache.start(messageId = 1L, pills = listOf(pill("👍")))
        advanceUntilIdle()
        cache.start(messageId = 1L, pills = emptyList())
        advanceUntilIdle()

        assertEquals(emptyList(), cache.rows(1L).first())
    }
}
