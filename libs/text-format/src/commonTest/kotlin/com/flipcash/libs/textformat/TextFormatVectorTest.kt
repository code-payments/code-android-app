package com.flipcash.libs.textformat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GATE: the parser must reproduce the canonical cross-platform fixture exactly.
 *
 * `text_format.json` is copied byte for byte from the orchestrator's `test-vectors/`, where
 * `gen_text_format.py` writes it. Compiled into `commonTest`, this runs on the JVM and on the iOS
 * simulator from one source.
 */
class TextFormatVectorTest {

    @Test
    fun fixtureIsTheExpectedCopy() {
        val digest = SHA256().digest(readTestResource("text_format.json").encodeToByteArray())
        val hex = digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        assertEquals(
            FIXTURE_SHA256,
            hex,
            "text_format.json differs from the orchestrator's copy; recopy it and update FIXTURE_SHA256",
        )
    }

    @Test
    fun parser_matches_canonical_vectors() {
        val root = Json.parseToJsonElement(readTestResource("text_format.json")).jsonObject
        val vectors = root["vectors"]!!.jsonArray
        assertEquals(VECTOR_COUNT, vectors.size, "vector count")

        for (el in vectors) {
            val v = el.jsonObject
            val name = v["name"]!!.jsonPrimitive.content
            val text = v["text"]!!.jsonPrimitive.content
            val ranges = v["ranges"]!!.jsonArray.map {
                val r = it.jsonObject
                ProtectedRange(r.int("start"), r.int("end"), r.kind())
            }

            val got = parseTextFormat(text, ranges)

            assertEquals(v["display"]!!.jsonPrimitive.content, got.display, "display for '$name'")
            assertEquals(expectedSpans(v["spans"]!!.jsonArray), got.spans, "spans for '$name'")
            assertEquals(
                expectedDisplayRanges(v["displayRanges"]!!.jsonArray),
                got.displayRanges,
                "displayRanges for '$name'",
            )
        }
        assertTrue(vectors.isNotEmpty(), "no vectors loaded")
    }

    private fun expectedSpans(array: JsonArray): List<StyledSpan> = array.map {
        val s = it.jsonObject
        StyledSpan(s.int("start"), s.int("end"), s["style"]!!.jsonPrimitive.content.toStyle())
    }

    private fun expectedDisplayRanges(array: JsonArray): List<DisplayRange> = array.map {
        val r = it.jsonObject
        DisplayRange(r.int("start"), r.int("end"), r.kind(), r["target"]?.jsonPrimitive?.content)
    }

    private fun JsonObject.int(key: String): Int = this[key]!!.jsonPrimitive.int

    private fun JsonObject.kind(): RangeKind = when (val k = this["kind"]!!.jsonPrimitive.content) {
        "link" -> RangeKind.Link
        "mention" -> RangeKind.Mention
        else -> error("unknown range kind '$k'")
    }

    private fun String.toStyle(): FormatStyle = when (this) {
        "bold" -> FormatStyle.Bold
        "italic" -> FormatStyle.Italic
        "strike" -> FormatStyle.Strike
        "code" -> FormatStyle.Code
        "codeBlock" -> FormatStyle.CodeBlock
        "quote" -> FormatStyle.Quote
        "bullet" -> FormatStyle.Bullet
        "numbered" -> FormatStyle.Numbered
        else -> error("unknown style '$this'")
    }

    private companion object {
        const val FIXTURE_SHA256 = "cde3d3baaf8d7f7028f91244cd9dd4ff311c256a7085d757b6ac81040b6a5eb6"
        const val VECTOR_COUNT = 122
    }
}
