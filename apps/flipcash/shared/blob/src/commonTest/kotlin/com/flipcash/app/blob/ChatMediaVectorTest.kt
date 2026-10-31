package com.flipcash.app.blob

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `test-vectors/chat_media.json` (git blob 54f976f2737e37811af0d9bba5a849e7ba3f7ade). The canonical
 * copy lives in the orchestrator repo and is byte-identical to iOS's; this one is synced. A failure
 * here is a real regression or a cross-platform decision to make in the canonical fixture, never a
 * local edit.
 *
 * Covers dimensions and policy choice, not encoded bytes, which differ by platform.
 */
class ChatMediaVectorTest {

    private val root: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResourceAsStream("chat_media.json")!!
            .bufferedReader().use { it.readText() }
    ).jsonObject

    private fun cases(key: String): JsonArray = root.getValue(key).jsonArray

    @Test
    fun `downscale matches the vectors`() {
        val cases = cases("downscale")
        // A fixture that failed to load reads as empty, and a loop over nothing passes.
        assertTrue(cases.isNotEmpty(), "chat_media.json has no downscale vectors")

        for (case in cases.map { it.jsonObject }) {
            val name = case.getValue("name").jsonPrimitive.content
            val expected = case.getValue("expected").jsonObject
            val actual = ChatMediaDownscale.target(
                width = case.getValue("sourceWidth").jsonPrimitive.int,
                height = case.getValue("sourceHeight").jsonPrimitive.int,
                maxWidth = case.getValue("maxWidth").jsonPrimitive.int,
                maxHeight = case.getValue("maxHeight").jsonPrimitive.int,
                maxPixels = case.getValue("maxPixels").jsonPrimitive.long,
            )
            assertEquals(
                PixelSize(expected.getValue("width").jsonPrimitive.int, expected.getValue("height").jsonPrimitive.int),
                actual,
                "downscale: $name",
            )
        }
    }

    @Test
    fun `constraint selection matches the vectors`() {
        val cases = cases("constraintSelection")
        assertTrue(cases.isNotEmpty(), "chat_media.json has no constraintSelection vectors")

        for (case in cases.map { it.jsonObject }) {
            val name = case.getValue("name").jsonPrimitive.content
            val expected = case.getValue("expectedIndex")
            val actual = ChatMediaConstraints.firstMatchIndex(
                patterns = case.getValue("patterns").jsonArray.map { it.jsonPrimitive.content },
                mimeType = case.getValue("mimeType").jsonPrimitive.content,
            )
            if (expected is JsonNull) {
                assertNull(actual, "constraintSelection: $name")
            } else {
                assertEquals(expected.jsonPrimitive.int, actual, "constraintSelection: $name")
            }
        }
    }

    @Test
    fun `quality ladder and upload mime type match the vectors`() {
        val ladder = root.getValue("jpegQualityLadder").jsonArray.map { (it.jsonPrimitive.double * 100).let(Math::round).toInt() }
        assertEquals(ladder, JpegLadder.QUALITIES)
        assertEquals(root.getValue("uploadMimeType").jsonPrimitive.contentOrNull, ChatMediaEncoder.UPLOAD_MIME_TYPE)
    }
}
