package com.flipcash.shared.chat.ui

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How a text bubble's body is laid out around its mentions. Robolectric for [detectUrls]. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MentionedTextTest {

    private fun build(text: String, onClick: ((String) -> Unit)? = {}) =
        buildMentionedText(text, detectMentions(text, detectUrls(text)), SpanStyle(), onClick)

    @Test
    fun `each handle sits between two spacing placeholders`() {
        val built = build("hi @jeff there")

        // "hi " + placeholder, then "@jeff", then placeholder + " there".
        assertEquals(listOf(TextRange(4, 9)), built.handles)
        assertEquals("@jeff", built.text.text.substring(4, 9))
        assertEquals(2, built.text.getStringAnnotations(0, built.text.length).count { it.item == MENTION_SPACING_SLOT })
    }

    @Test
    fun `the tap covers the pill's spacing as well as the letters`() {
        var tapped: String? = null
        val built = build("hi @Jeff", onClick = { tapped = it })

        val link = built.text.getLinkAnnotations(0, built.text.length).single()
        assertEquals(3 to 10, link.start to link.end)
        (link.item as LinkAnnotation.Clickable).linkInteractionListener!!.onClick(link.item)
        assertEquals("jeff", tapped)
    }

    @Test
    fun `a link after a mention moves by the mention's placeholders`() {
        val built = build("@jeff see https://example.com")

        val url = built.text.getLinkAnnotations(0, built.text.length)
            .single { it.item is LinkAnnotation.Url }
        assertEquals("https://example.com", built.text.text.substring(url.start, url.end))
    }

    @Test
    fun `nothing is tappable where mentions cannot be opened, but the pill is still drawn`() {
        val built = build("hi @jeff", onClick = null)

        assertTrue(built.text.getLinkAnnotations(0, built.text.length).isEmpty())
        assertEquals(1, built.handles.size)
    }

    @Test
    fun `text with no mention is unchanged`() {
        val built = build("hello there")

        assertEquals("hello there", built.text.text)
        assertTrue(built.handles.isEmpty())
    }
}
