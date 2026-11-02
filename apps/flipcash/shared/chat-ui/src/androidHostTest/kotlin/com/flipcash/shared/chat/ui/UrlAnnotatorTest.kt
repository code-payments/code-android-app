package com.flipcash.shared.chat.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UrlAnnotatorTest {

    private val linkStyle = SpanStyle(
        color = Color.Blue,
        textDecoration = TextDecoration.Underline,
    )
    private val annotator = UrlAnnotator(linkStyle)

    private fun annotate(text: String) = buildAnnotatedString {
        append(text)
        with(annotator) { annotate(text) }
    }

    @Test
    fun `annotates https URL at correct offsets`() {
        val text = "Visit https://example.com today"
        val result = annotate(text)

        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)

        val link = links.first()
        assertEquals(6, link.start)
        assertEquals(25, link.end)

        val annotation = link.item as LinkAnnotation.Url
        assertEquals("https://example.com", annotation.url)
    }

    @Test
    fun `annotates http URL without adding https`() {
        val text = "Go to http://example.com now"
        val result = annotate(text)

        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)

        val annotation = links.first().item as LinkAnnotation.Url
        assertEquals("http://example.com", annotation.url)
    }

    @Test
    fun `prepends https for bare domain`() {
        val text = "Check example.com out"
        val result = annotate(text)

        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)

        val annotation = links.first().item as LinkAnnotation.Url
        assertEquals("https://example.com", annotation.url)
    }

    @Test
    fun `annotates multiple URLs`() {
        val text = "See https://a.com and https://b.com"
        val result = annotate(text)

        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(2, links.size)

        val urls = links.map { (it.item as LinkAnnotation.Url).url }
        assertTrue("https://a.com" in urls)
        assertTrue("https://b.com" in urls)
    }

    @Test
    fun `no annotations for plain text`() {
        val text = "Just a plain message"
        val result = annotate(text)

        val links = result.getLinkAnnotations(0, result.length)
        assertTrue(links.isEmpty())
    }
}
