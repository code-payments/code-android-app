package com.flipcash.shared.chat.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Detection half of `test-vectors/link_detection.json`. The canonical copy lives in the
 * orchestrator repo; this one is synced. A failure here is either a real regression or a
 * deliberate cross-platform decision that has to be made in the canonical fixture and
 * re-synced to both platforms — never a local edit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkDetectionVectorTest {

    private fun vectors(): List<JSONObject> {
        val json = javaClass.classLoader!!
            .getResourceAsStream("link_detection.json")!!
            .bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("vectors")
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    @Test
    fun `spans match the cross-platform vectors`() {
        val annotator = UrlAnnotator(SpanStyle())
        for (vector in vectors()) {
            val name = vector.getString("name")
            val text = vector.getString("text")

            val built = AnnotatedString.Builder().apply {
                append(text)
                with(annotator) { annotate(text) }
            }.toAnnotatedString()

            val actual = built.getLinkAnnotations(0, text.length).map { range ->
                Triple(range.start, range.end, (range.item as LinkAnnotation.Url).url)
            }

            val expectedArray = vector.getJSONArray("spans")
            val expected = (0 until expectedArray.length()).map { i ->
                val span = expectedArray.getJSONObject(i)
                Triple(span.getInt("start"), span.getInt("end"), span.getString("url"))
            }

            assertEquals(expected, actual, "vector `$name`: ${vector.getString("note")}")
        }
    }
}
