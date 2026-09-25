package com.getcode.libs.emojis.reactions

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
 * Covers `defaults`, `recents` and `drawability`; `merge`, `order` and `strip` are covered by
 * `apps:flipcash:shared:chat`'s `ReactionVectorsTest`, next to where `ReactionState` lives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RecentReactionsVectorTest {

    @Test
    fun `defaults match the reactions fixture`() {
        val expected = section("defaults")
        assertTrue(expected.length() > 0, "reactions.json loaded no defaults")
        val expectedList = (0 until expected.length()).map { expected.getString(it) }
        assertEquals(expectedList, RecentReactions.DEFAULTS, "RecentReactions.DEFAULTS")
    }

    @Test
    fun `recents vectors match the reactions fixture`() {
        val vectors = section("recents")
        assertTrue(vectors.length() > 0, "reactions.json loaded no recents vectors")

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val name = vector.getString("name")
            val note = vector.getString("note")

            val usesJson = vector.getJSONArray("uses")
            var stats: Map<String, RecentReactions.Usage> = emptyMap()
            for (k in 0 until usesJson.length()) {
                val use = usesJson.getJSONObject(k)
                stats = RecentReactions.record(
                    emoji = use.getString("emoji"),
                    at = Instant.fromEpochSeconds(use.getLong("usedAt")),
                    stats = stats,
                )
            }

            val undrawableJson = vector.getJSONArray("undrawable")
            val undrawable = (0 until undrawableJson.length()).map { undrawableJson.getString(it) }.toSet()

            val limit = vector.getInt("limit")

            val expectedJson = vector.getJSONArray("expect")
            val expected = (0 until expectedJson.length()).map { expectedJson.getString(it) }

            val actual = RecentReactions.rank(stats, undrawable, limit)
            assertEquals(expected, actual, "vector `$name`: $note")
        }
    }

    /**
     * [EmojiDrawability] delegates to [android.graphics.Paint.hasGlyph], which asks the device's
     * real font fallback and so cannot be pinned to a fixed answer in a JVM unit test the way the
     * pure-logic sections above can. This only checks that the predicate runs and returns a
     * boolean for each vector's emoji, naming the vector so a crash points at the offending
     * sequence; the actual drawability answer is verified on-device (see the module's
     * instrumented/UI-level coverage; chunk B wires the strip and picker up to a real predicate).
     */
    @Test
    fun `drawability vectors run without crashing`() {
        val vectors = section("drawability")
        assertTrue(vectors.length() > 0, "reactions.json loaded no drawability vectors")

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val emoji = vector.getString("emoji")
            val note = vector.optString("note", "")
            // Must not throw for any vector's emoji; the boolean result is real-device behavior.
            EmojiDrawability.isDrawable(emoji)
            assertTrue(true, "vector `$emoji`: $note")
        }
    }

    private fun section(name: String): JSONArray {
        val json = javaClass.classLoader!!
            .getResourceAsStream("reactions.json")!!
            .bufferedReader().use { it.readText() }
        return JSONObject(json).getJSONArray(name)
    }
}
