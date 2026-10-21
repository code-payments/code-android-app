package com.flipcash.shared.chat.reactions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReactionBoostStateTest {

    private val now = Instant.fromEpochSeconds(1_000)

    private fun entry(
        emoji: String = "💯",
        count: Long = 1,
        selfReacted: Boolean = false,
        version: Long = 1,
        boost: Long = 0,
        selfBoost: Long = 0,
    ) = ReactionState.SummaryEntry(
        emoji = emoji,
        count = count,
        selfReacted = selfReacted,
        version = version,
        selfReactedAt = if (selfReacted) now else null,
        boost = boost,
        selfBoost = selfBoost,
    )

    @Test
    fun `summary boost shows on the pill`() {
        val state = ReactionState()
        state.applySummary(listOf(entry(count = 2, boost = 1_000)))

        val pill = state.pills.single()
        assertEquals(ReactionBoost(1_000), pill.boost)
        assertFalse(pill.selfBoosted)
    }

    @Test
    fun `a tap on the user's own boosted reaction does nothing`() {
        val state = ReactionState()
        state.applySummary(listOf(entry(selfReacted = true, boost = 500, selfBoost = 500)))

        assertNull(state.tap("💯", now))
        assertTrue(state.pills.single().selfReacted)
        assertTrue(state.pills.single().selfBoosted)
    }

    @Test
    fun `a tap on an unboosted reaction still removes it`() {
        val state = ReactionState()
        state.applySummary(listOf(entry(selfReacted = true, boost = 500)))

        assertEquals(ReactionCall.Op.REMOVE, state.tap("💯", now)?.op)
    }

    @Test
    fun `a BOOSTED update by the user marks the reaction boosted`() {
        val state = ReactionState()
        state.applySummary(listOf(entry(selfReacted = true, version = 1)))

        state.applyUpdate(
            emoji = "💯", actorIsSelf = true, added = true, count = 1, version = 2,
            reactedAt = null, boost = 100, actorBoost = 100,
        )

        val pill = state.pills.single()
        assertEquals(ReactionBoost(100), pill.boost)
        assertTrue(pill.selfBoosted)
        assertTrue(pill.selfReacted)
    }

    @Test
    fun `another user's BOOSTED update keeps the user's own boost`() {
        val state = ReactionState()
        state.applySummary(listOf(entry(selfReacted = true, version = 1, boost = 100, selfBoost = 100)))

        state.applyUpdate(
            emoji = "💯", actorIsSelf = false, added = true, count = 2, version = 2,
            reactedAt = null, boost = 600, actorBoost = 500,
        )

        val pill = state.pills.single()
        assertEquals(ReactionBoost(600), pill.boost)
        assertTrue(pill.selfBoosted)
    }

    @Test
    fun `the user's own ADDED update keeps the boost it does not carry`() {
        val state = ReactionState()
        state.applySummary(listOf(entry(selfReacted = true, version = 1, boost = 100, selfBoost = 100)))

        state.applyUpdate(
            emoji = "💯", actorIsSelf = true, added = true, count = 1, version = 2,
            reactedAt = now, boost = 100, actorBoost = 0,
        )

        assertTrue(state.pills.single().selfBoosted)
    }

    @Test
    fun `an add response keeps the boost it does not carry`() {
        val state = ReactionState()
        state.applySummary(listOf(entry(count = 1, version = 1, boost = 500)))

        val call = state.tap("💯", now)!!
        state.respond(call.emoji, ReactionResult.Ok(count = 2, selfReacted = true, version = 2, selfReactedAt = now))

        val pill = state.pills.single()
        assertEquals(ReactionBoost(500), pill.boost)
        assertFalse(pill.selfBoosted)
    }

    @Test
    fun `a refused remove of a boosted reaction rolls back without an error`() {
        assertNull(ReactionFailure.CANNOT_REMOVE_BOOSTED.userError)
    }
}
