package com.flipcash.shared.chat.ui

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bubble's body built from a parsed message: styles and links at the right offsets, mention
 * placeholders accounted for. Robolectric for [detectUrls].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FormattedBubbleTextTest {

    private val linkStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    private val mentionStyle = SpanStyle(textDecoration = TextDecoration.None)

    private fun build(text: String) = buildMentionedText(
        text = text,
        mentions = detectMentions(text, detectUrls(text)),
        linkStyle = linkStyle,
        mentionStyle = mentionStyle,
        onMentionClick = {},
    )

    private fun MentionedText.styled(predicate: (SpanStyle) -> Boolean): List<String> =
        text.spanStyles.filter { predicate(it.item) }.map { text.text.substring(it.start, it.end) }

    @Test
    fun `markers are consumed and each style lands on its words`() {
        val built = build("*bold* _italic_ ~gone~ `code`")

        assertEquals("bold italic gone code", built.text.text)
        assertEquals(listOf("bold"), built.styled { it.fontWeight == FontWeight.Bold })
        assertEquals(listOf("italic"), built.styled { it.fontStyle == FontStyle.Italic })
        assertEquals(listOf("gone"), built.styled { it.textDecoration == TextDecoration.LineThrough })
        assertEquals(listOf("code"), built.styled { it.fontFamily != null })
    }

    @Test
    fun `styles nest`() {
        val built = build("*bold _and italic_*")

        assertEquals(listOf("bold and italic"), built.styled { it.fontWeight == FontWeight.Bold })
        assertEquals(listOf("and italic"), built.styled { it.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `a bold span that straddles a mention takes the mention's placeholders with it`() {
        val built = build("*hi @jeff there*")

        // "hi " + placeholder + "@jeff" + placeholder + " there".
        assertEquals(listOf(TextRange(4, 9)), built.handles)
        val bold = built.text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0 to built.text.length, bold.start to bold.end)
    }

    @Test
    fun `a style after a mention moves past both placeholders`() {
        val built = build("@jeff is *here*")

        val bold = built.text.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("here", built.text.text.substring(bold.start, bold.end))
    }

    @Test
    fun `a style can wrap a whole mention`() {
        val built = build("_@jeff_")

        assertEquals(1, built.handles.size)
        assertEquals("@jeff", built.text.text.substring(built.handles.single().start, built.handles.single().end))
        val italic = built.text.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertEquals(0 to built.text.length, italic.start to italic.end)
    }

    @Test
    fun `a masked link shows its label and opens its target`() {
        val built = build("see [the docs](https://example.com/docs) now")

        assertEquals("see the docs now", built.text.text)
        val link = built.text.getLinkAnnotations(0, built.text.length).single()
        assertEquals("the docs", built.text.text.substring(link.start, link.end))
        assertEquals("https://example.com/docs", (link.item as LinkAnnotation.Url).url)
    }

    @Test
    fun `a bare link keeps opening its own resolved address`() {
        val built = build("*example.com/a*")

        val link = built.text.getLinkAnnotations(0, built.text.length).single()
        assertEquals("example.com/a", built.text.text.substring(link.start, link.end))
        assertEquals("https://example.com/a", (link.item as LinkAnnotation.Url).url)
    }

    @Test
    fun `list markers stay and wrapped lines are indented`() {
        val built = build("- one\n1. two\nplain")

        assertEquals("- one\n1. two\nplain", built.text.text)
        val indents = built.text.paragraphStyles.map { it.item.textIndent!!.restLine }
        assertEquals(listOf(FormatStyles().bulletIndent, FormatStyles().numberedIndent), indents)
    }

    @Test
    fun `consecutive quoted lines get one bar`() {
        val built = build("> one\n> two\nafter\n> again")

        assertEquals("one\ntwo\nafter\nagain", built.text.text)
        assertEquals(listOf(TextRange(0, 7), TextRange(14, 19)), built.quoteBars)
    }

    @Test
    fun `a quoted list item is indented by both`() {
        val styles = FormatStyles()
        val built = build("> - a")

        val indent = built.text.paragraphStyles.single().item.textIndent!!
        assertEquals(styles.quoteIndent, indent.firstLine)
        assertEquals((styles.quoteIndent.value + styles.bulletIndent.value).sp, indent.restLine)
    }

    @Test
    fun `text with no markup is unchanged and carries no styling`() {
        val built = build("just words, 2*3*4 and snake_case_name")

        assertEquals("just words, 2*3*4 and snake_case_name", built.text.text)
        assertTrue(built.text.spanStyles.isEmpty())
        assertTrue(built.quoteBars.isEmpty())
    }

    @Test
    fun `the display text drops markers and nothing else`() {
        assertEquals("hi there", formattedDisplayText("*hi* _there_"))
        assertEquals("one\n- two", formattedDisplayText("> one\n- two"))
    }

    @Test
    fun `a masked link never offers a card, a literal one does`() {
        val cash = "https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"
        val masked = "[free money]($cash)"
        assertEquals(emptyList(), cardLinks(masked))

        val literal = "[free money] ($cash)"
        assertEquals(listOf(cash), cardLinks(literal).map { it.url })
    }
}
