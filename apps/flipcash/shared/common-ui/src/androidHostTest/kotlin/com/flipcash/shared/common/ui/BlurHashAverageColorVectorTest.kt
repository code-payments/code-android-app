package com.flipcash.shared.common.ui

import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `test-vectors/blurhash_average.json`. The canonical copy lives in the orchestrator repo; this one
 * is synced. A failure is fixed in the canonical fixture and re-synced to both platforms, never
 * edited here.
 *
 * The group invite link card tints its band with this colour, so both apps must read the same
 * colour from the same hash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BlurHashAverageColorVectorTest {

    private fun fixture(): JSONObject = JSONObject(
        javaClass.classLoader!!
            .getResourceAsStream("blurhash_average.json")!!
            .bufferedReader().use { it.readText() }
    )

    @Test
    fun `every vector reads the expected average colour`() {
        val fixture = fixture()
        assertEquals("blurhash-average-color", fixture.getString("algorithm"))
        val vectors = fixture.getJSONArray("vectors")
        assertTrue(vectors.length() > 0)

        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val expected = vector.optJSONObject("average")?.let {
                (it.getInt("red") shl 16) or (it.getInt("green") shl 8) or it.getInt("blue")
            }
            assertEquals(
                expected,
                BlurHash.averageColor(vector.getString("blurHash")),
                "vector `${vector.getString("name")}`",
            )
        }
    }

    @Test
    fun `a missing hash has no average colour`() {
        assertNull(BlurHash.averageColor(null))
    }
}
