package com.getcode.libs.emojis.reactions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * `test-vectors/reactions.json`, read in place through this module's test resources. It is a
 * verbatim copy of the orchestrator's canonical fixture (#17). A failure here is either a real regression or a cross-platform decision that
 * has to be made in the canonical fixture and re-synced to both platforms — never a local edit.
 *
 * Covers `defaults` and `recents`; `merge`, `order` and `strip` are covered by
 * `apps:flipcash:shared:chat`'s `ReactionVectorsTest`, next to where `ReactionState` lives, and
 * `drawability` by the instrumented `EmojiDrawabilityVectorTest`, which needs a real emoji font.
 */
class RecentReactionsVectorTest {

    @Test
    fun `defaults match the reactions fixture`() {
        val expected = section("defaults")
        assertTrue(expected.size > 0, "reactions.json loaded no defaults")
        val expectedList = (0 until expected.size).map { expected[it].jsonPrimitive.content }
        assertEquals(expectedList, RecentReactions.DEFAULTS, "RecentReactions.DEFAULTS")
    }

    @Test
    fun `recents vectors match the reactions fixture`() {
        val vectors = section("recents")
        assertTrue(vectors.size > 0, "reactions.json loaded no recents vectors")

        for (i in 0 until vectors.size) {
            val vector = vectors[i].jsonObject
            val name = vector.getValue("name").jsonPrimitive.content
            val note = vector.getValue("note").jsonPrimitive.content

            val usesJson = vector.getValue("uses").jsonArray
            var stats: Map<String, RecentReactions.Usage> = emptyMap()
            for (k in 0 until usesJson.size) {
                val use = usesJson[k].jsonObject
                stats = RecentReactions.record(
                    emoji = use.getValue("emoji").jsonPrimitive.content,
                    at = Instant.fromEpochSeconds(use.getValue("usedAt").jsonPrimitive.long),
                    stats = stats,
                )
            }

            val undrawableJson = vector.getValue("undrawable").jsonArray
            val undrawable = (0 until undrawableJson.size).map { undrawableJson[it].jsonPrimitive.content }.toSet()

            val limit = vector.getValue("limit").jsonPrimitive.int

            val expectedJson = vector.getValue("expect").jsonArray
            val expected = (0 until expectedJson.size).map { expectedJson[it].jsonPrimitive.content }

            val actual = RecentReactions.rank(stats, undrawable, limit)
            assertEquals(expected, actual, "vector `$name`: $note")
        }
    }

    private fun section(name: String): JsonArray {
        val json = javaClass.classLoader!!
            .getResourceAsStream("reactions.json")!!
            .bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(json).jsonObject.getValue(name).jsonArray
    }
}
