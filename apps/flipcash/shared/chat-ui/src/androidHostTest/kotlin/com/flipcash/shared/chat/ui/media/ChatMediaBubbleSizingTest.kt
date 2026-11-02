package com.flipcash.shared.chat.ui.media

import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `bubble` half of `test-vectors/chat_media.json`. The canonical copy lives in the orchestrator
 * repo; this one is synced, never edited locally.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatMediaBubbleSizingTest {

    private val root: JSONObject by lazy {
        val json = javaClass.classLoader!!
            .getResourceAsStream("chat_media.json")!!
            .bufferedReader().use { it.readText() }
        JSONObject(json)
    }

    @Test
    fun `aspect limits match the fixture`() {
        assertEquals(root.getDouble("minAspect").toFloat(), ChatMediaBubbleSizing.MIN_ASPECT)
        assertEquals(root.getDouble("maxAspect").toFloat(), ChatMediaBubbleSizing.MAX_ASPECT)
    }

    @Test
    fun `every bubble case matches the fixture`() {
        val cases = root.getJSONArray("bubble")
        assertTrue(cases.length() > 0)
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val name = case.getString("name")
            val expected = case.getJSONObject("expected")
            val actual = ChatMediaBubbleSizing.size(
                imageWidth = case.getInt("imageWidth"),
                imageHeight = case.getInt("imageHeight"),
                maxWidth = case.getDouble("maxWidth").toFloat(),
            )
            assertEquals(expected.getDouble("width").toFloat(), actual.width, 0.001f, "$name width")
            assertEquals(expected.getDouble("height").toFloat(), actual.height, 0.001f, "$name height")
            assertEquals(expected.getBoolean("cropped"), actual.cropped, "$name cropped")
        }
    }

    @Test
    fun `null dimensions give a square`() {
        val size = ChatMediaBubbleSizing.size(null, null, 240f)
        assertEquals(ChatMediaBubbleSize(240f, 240f, cropped = false), size)
    }
}
