package com.flipcash.shared.chat.reactions

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `test-vectors/reactions.json`. The canonical copy lives in the orchestrator repo (#17); this one
 * is synced verbatim. A failure here is either a real regression or a cross-platform decision that
 * has to be made in the canonical fixture and re-synced to both platforms — never a local edit.
 *
 * Covers the `merge`, `order` and `strip` sections; `defaults`, `recents` and `drawability` are
 * covered by `libs:emojis`'s `RecentReactionsVectorTest`, next to where `RecentReactions` lives.
 *
 * `merge` replays each vector's steps against a fresh [ReactionState] — the same state machine as
 * iOS's `FlipcashCore` `ReactionState` and `gen_reactions.py`'s reference `Model` — and checks the
 * resulting pills, the calls it would send, and the errors it would show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReactionVectorsTest {

    @Test
    fun `merge vectors match the reactions fixture`() {
        val vectors = section("merge")
        assertTrue(vectors.length() > 0, "reactions.json loaded no merge vectors")

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val name = vector.getString("name")
            val note = vector.getString("note")
            val self = vector.getString("self")

            val state = ReactionState()
            val calls = mutableListOf<ReactionCall>()
            val errors = mutableListOf<ReactionError>()

            val steps = vector.getJSONArray("steps")
            for (s in 0 until steps.length()) {
                val step = steps.getJSONObject(s)
                when (val op = step.getString("op")) {
                    "summary" -> {
                        val reactions = step.getJSONArray("reactions")
                        val entries = (0 until reactions.length()).map { k ->
                            val r = reactions.getJSONObject(k)
                            ReactionState.SummaryEntry(
                                emoji = r.getString("emoji"),
                                count = r.getLong("count"),
                                selfReacted = r.getBoolean("self"),
                                version = r.getLong("version"),
                                selfReactedAt = null,
                            )
                        }
                        state.applySummary(entries)
                    }

                    "update" -> {
                        state.applyUpdate(
                            emoji = step.getString("emoji"),
                            actorIsSelf = step.getString("actor") == self,
                            added = step.getString("action") == "ADDED",
                            count = step.getLong("count"),
                            version = step.getLong("version"),
                            reactedAt = EPOCH,
                        )
                    }

                    "tap" -> {
                        val call = state.tap(step.getString("emoji"), at = EPOCH)
                        if (call != null) calls.add(call)
                    }

                    "respond" -> {
                        val result = step.getString("result")
                        val outcome = if (result == "OK") {
                            val reaction = step.getJSONObject("reaction")
                            ReactionResult.Ok(
                                count = reaction.getLong("count"),
                                selfReacted = reaction.getBoolean("self"),
                                version = reaction.getLong("version"),
                                selfReactedAt = null,
                            )
                        } else {
                            ReactionResult.Failed(failureOf(result))
                        }
                        val (call, error) = state.respond(step.getString("emoji"), outcome)
                        if (call != null) calls.add(call)
                        if (error != null) errors.add(error)
                    }

                    else -> error("unknown op `$op`")
                }
            }

            val expect = vector.getJSONObject("expect")
            assertPills(expect.getJSONArray("pills"), state.pills, name, note)
            assertCalls(expect.getJSONArray("calls"), calls, name, note)
            assertErrors(expect.getJSONArray("errors"), errors, name, note)
        }
    }

    @Test
    fun `order vectors match the reactions fixture`() {
        val vectors = section("order")
        assertTrue(vectors.length() > 0, "reactions.json loaded no order vectors")

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val name = vector.getString("name")
            val note = vector.getString("note")

            val pillsJson = vector.getJSONArray("pills")
            val pills = (0 until pillsJson.length()).map { k ->
                val p = pillsJson.getJSONObject(k)
                val boostTotal = p.getLong("boostTotal")
                ReactionPill(
                    emoji = p.getString("emoji"),
                    count = p.getLong("count"),
                    selfReacted = false,
                    pending = false,
                    boost = if (boostTotal != 0L) ReactionBoost(boostTotal) else null,
                )
            }

            val expected = vector.getJSONArray("expect")
            val expectedOrder = (0 until expected.length()).map { expected.getString(it) }
            val actualOrder = ReactionOrdering.sorted(pills).map { it.emoji }
            assertEquals(expectedOrder, actualOrder, "vector `$name`: $note")
        }
    }

    @Test
    fun `strip vectors match the reactions fixture`() {
        val vectors = section("strip")
        assertTrue(vectors.length() > 0, "reactions.json loaded no strip vectors")

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val name = vector.getString("name")
            val note = vector.getString("note")

            val recentsJson = vector.getJSONArray("recents")
            val recents = (0 until recentsJson.length()).map { recentsJson.getString(it) }

            val selfReactionsJson = vector.getJSONArray("selfReactions")
            val selfReactions = (0 until selfReactionsJson.length()).map { k ->
                val r = selfReactionsJson.getJSONObject(k)
                SelfReaction(emoji = r.getString("emoji"), reactedAt = Instant.fromEpochSeconds(r.getLong("reactedAt")))
            }

            val expectJson = vector.getJSONArray("expect")
            val expected = (0 until expectJson.length()).map { k ->
                val e = expectJson.getJSONObject(k)
                ReactionStrip.Entry(emoji = e.getString("emoji"), highlighted = e.getBoolean("highlighted"))
            }

            val actual = ReactionStrip.entries(recents, selfReactions)
            assertEquals(expected, actual, "vector `$name`: $note")
        }
    }

    private fun assertPills(expected: JSONArray, actual: List<ReactionPill>, name: String, note: String) {
        assertEquals(expected.length(), actual.size, "vector `$name` pill count: $note")
        for (k in 0 until expected.length()) {
            val e = expected.getJSONObject(k)
            val a = actual[k]
            assertEquals(e.getString("emoji"), a.emoji, "vector `$name` pill[$k].emoji: $note")
            assertEquals(e.getLong("count"), a.count, "vector `$name` pill[$k].count: $note")
            assertEquals(e.getBoolean("selfReacted"), a.selfReacted, "vector `$name` pill[$k].selfReacted: $note")
            assertEquals(e.getBoolean("pending"), a.pending, "vector `$name` pill[$k].pending: $note")
        }
    }

    private fun assertCalls(expected: JSONArray, actual: List<ReactionCall>, name: String, note: String) {
        assertEquals(expected.length(), actual.size, "vector `$name` call count: $note")
        for (k in 0 until expected.length()) {
            val e = expected.getJSONObject(k)
            val a = actual[k]
            val expectedOp = if (e.getString("op") == "add") ReactionCall.Op.ADD else ReactionCall.Op.REMOVE
            assertEquals(expectedOp, a.op, "vector `$name` call[$k].op: $note")
            assertEquals(e.getString("emoji"), a.emoji, "vector `$name` call[$k].emoji: $note")
        }
    }

    private fun assertErrors(expected: JSONArray, actual: List<ReactionError>, name: String, note: String) {
        assertEquals(expected.length(), actual.size, "vector `$name` error count: $note")
        for (k in 0 until expected.length()) {
            val expectedError = when (val wire = expected.getString(k)) {
                "reactionFailed" -> ReactionError.REACTION_FAILED
                "tooManyReactionTypes" -> ReactionError.TOO_MANY_REACTION_TYPES
                else -> error("unknown error `$wire`")
            }
            assertEquals(expectedError, actual[k], "vector `$name` error[$k]: $note")
        }
    }

    private fun failureOf(result: String): ReactionFailure = when (result) {
        "NETWORK" -> ReactionFailure.NETWORK
        "DENIED" -> ReactionFailure.DENIED
        "MESSAGE_NOT_FOUND" -> ReactionFailure.MESSAGE_NOT_FOUND
        "CANNOT_REACT" -> ReactionFailure.CANNOT_REACT
        "TOO_MANY_REACTION_TYPES" -> ReactionFailure.TOO_MANY_REACTION_TYPES
        else -> error("unknown result `$result`")
    }

    private fun section(name: String): JSONArray {
        val json = javaClass.classLoader!!
            .getResourceAsStream("reactions.json")!!
            .bufferedReader().use { it.readText() }
        return JSONObject(json).getJSONArray(name)
    }

    private companion object {
        val EPOCH: Instant = Instant.fromEpochSeconds(0)
    }
}
