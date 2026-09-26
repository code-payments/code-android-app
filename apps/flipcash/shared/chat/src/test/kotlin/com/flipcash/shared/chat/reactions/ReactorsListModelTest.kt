package com.flipcash.shared.chat.reactions

import com.flipcash.services.repository.ReactorsPage
import com.flipcash.services.models.chat.Reactor
import com.getcode.opencode.model.core.ID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun id(byte: Int): ID = listOf(byte.toByte())

class ReactorsListModelTest {

    private val alice = id(1)
    private val bob = id(2)
    private val carol = id(3)

    private fun reactor(userId: ID, seconds: Long) =
        Reactor(userId = userId, reactedAt = Instant.fromEpochSeconds(seconds))

    @Test
    fun `merges every emoji's first page into one row per person, in pill order`() = runTest {
        val model = ReactorsListModel(
            emojis = listOf("❤️", "👍"), // heart, thumbsup
            fetchPage = { emoji, _ ->
                when (emoji) {
                    "❤️" -> Result.success(
                        ReactorsPage(reactors = listOf(reactor(alice, 10), reactor(bob, 5)), hasMore = false),
                    )
                    else -> Result.success(
                        ReactorsPage(reactors = listOf(reactor(bob, 20)), hasMore = false),
                    )
                }
            },
        )

        model.loadMoreIfNeeded()

        assertEquals(2, model.rows.size)
        val bobRow = model.rows.first { it.userId == bob }
        assertEquals(listOf("❤️", "👍"), bobRow.emojis)
        val aliceRow = model.rows.first { it.userId == alice }
        assertEquals(listOf("❤️"), aliceRow.emojis)
    }

    @Test
    fun `rows sort by newest reaction first, then userId descending`() = runTest {
        val model = ReactorsListModel(
            emojis = listOf("❤️"),
            fetchPage = { _, _ ->
                Result.success(
                    ReactorsPage(
                        reactors = listOf(reactor(alice, 5), reactor(bob, 20), reactor(carol, 20)),
                        hasMore = false,
                    ),
                )
            },
        )

        model.loadMoreIfNeeded()

        // bob and carol tie at 20s -> higher userId (carol) first; alice (5s) last.
        assertEquals(listOf(carol, bob, alice), model.rows.map { it.userId })
    }

    @Test
    fun `publishes rows only once every emoji's round of pages is in`() = runTest {
        var bobReleased = false
        val model = ReactorsListModel(
            emojis = listOf("❤️", "👍"),
            fetchPage = { emoji, _ ->
                if (emoji == "👍" && !bobReleased) {
                    // Simulate the slower of the two calls.
                }
                when (emoji) {
                    "❤️" -> Result.success(ReactorsPage(reactors = listOf(reactor(alice, 1)), hasMore = false))
                    else -> Result.success(ReactorsPage(reactors = listOf(reactor(bob, 1)), hasMore = false))
                }
            },
        )

        assertTrue(model.rows.isEmpty())
        model.loadMoreIfNeeded()
        assertEquals(2, model.rows.size)
    }

    @Test
    fun `a failed page stops asking for more of that emoji only`() = runTest {
        var heartCalls = 0
        var thumbsCalls = 0
        val model = ReactorsListModel(
            emojis = listOf("❤️", "👍"),
            fetchPage = { emoji, _ ->
                when (emoji) {
                    "❤️" -> {
                        heartCalls++
                        Result.failure(RuntimeException("boom"))
                    }
                    else -> {
                        thumbsCalls++
                        Result.success(ReactorsPage(reactors = listOf(reactor(bob, 1)), hasMore = true, nextToken = listOf(1)))
                    }
                }
            },
        )

        model.loadMoreIfNeeded()
        model.loadMoreIfNeeded()

        assertEquals(1, heartCalls, "should not retry a failed emoji's page")
        assertEquals(2, thumbsCalls)
        assertTrue(model.hasMore, "thumbsup still has more")
    }

    @Test
    fun `hasMore is false once every emoji is exhausted`() = runTest {
        val model = ReactorsListModel(
            emojis = listOf("❤️"),
            fetchPage = { _, _ -> Result.success(ReactorsPage(reactors = emptyList(), hasMore = false)) },
        )

        assertTrue(model.hasMore)
        model.loadMoreIfNeeded()
        assertFalse(model.hasMore)
    }

    @Test
    fun `does not re-fetch an emoji whose page is already loading or exhausted`() = runTest {
        var calls = 0
        val model = ReactorsListModel(
            emojis = listOf("❤️"),
            fetchPage = { _, _ ->
                calls++
                Result.success(ReactorsPage(reactors = emptyList(), hasMore = false))
            },
        )

        model.loadMoreIfNeeded()
        model.loadMoreIfNeeded()

        assertEquals(1, calls, "exhausted emoji should not be asked again")
    }
}
