package com.getcode.libs.emojis.reactions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the `drawability` vectors from flipcash-client-orchestrator#17 against the device's emoji
 * font. Instrumented because Robolectric's Paint has no real font to shape a ZWJ sequence with.
 *
 * The mixed-skin-tone vector needs Emoji 12.1, which Android ships from API 30.
 */
@RunWith(AndroidJUnit4::class)
class EmojiDrawabilityVectorTest {

    @Test
    fun drawabilityVectors() {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open("reactions.json").bufferedReader().use { it.readText() }
        val vectors = JSONObject(json).getJSONArray("drawability")
        val failures = (0 until vectors.length()).map { vectors.getJSONObject(it) }.mapNotNull { vector ->
            val emoji = vector.getString("emoji")
            val expected = vector.getBoolean("drawable")
            val actual = EmojiDrawability.isDrawable(emoji)
            if (actual == expected) null else "$emoji (${vector.getString("note")}): expected $expected, got $actual"
        }
        assertEquals("drawability vectors failed:\n" + failures.joinToString("\n"), emptyList<String>(), failures)
    }
}
