package com.flipcash.shared.chat.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/**
 * Detection half of `test-vectors/link_detection.json`. The canonical copy lives in the
 * orchestrator repo; this one is synced. A failure is either a real regression or a deliberate
 * cross-platform decision that has to be made in the canonical fixture and re-synced to both
 * platforms, never a local edit.
 *
 * The JVM and Android tests both call this, so the ported pattern on desktop is held to the same
 * spans as `Patterns.WEB_URL`.
 */
internal fun assertSpansMatchVectors(json: String) {
    val vectors = Json.parseToJsonElement(json).jsonObject.getValue("vectors").jsonArray
    val failures = vectors.mapNotNull { element ->
        val vector: JsonObject = element.jsonObject
        val text = vector.getValue("text").jsonPrimitive.content
        val expected = vector.getValue("spans").jsonArray.map {
            val span = it.jsonObject
            Triple(span.getValue("start").jsonPrimitive.int, span.getValue("end").jsonPrimitive.int, span.getValue("url").jsonPrimitive.content)
        }
        val actual = detectUrls(text).map { Triple(it.start, it.end, it.url) }
        if (expected == actual) {
            null
        } else {
            "vector `${vector.getValue("name").jsonPrimitive.content}`: expected $expected but was $actual. " +
                vector.getValue("note").jsonPrimitive.content
        }
    }
    assertEquals(emptyList(), failures, failures.joinToString("\n"))
}
