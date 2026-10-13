package com.flipcash.libs.textformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposerFormattingTest {

    private fun inline(text: String, from: Int, to: Int, format: InlineFormat) =
        toggleInline(text, from, to, format)

    @Test
    fun `a selection is wrapped and stays selected`() {
        val edit = inline("say hello now", 4, 9, InlineFormat.Bold)!!
        assertEquals("say *hello* now", edit.text)
        assertEquals(5 to 10, edit.selectionStart to edit.selectionEnd)
    }

    @Test
    fun `whitespace at the ends of the selection stays outside the markers`() {
        assertEquals("say *hello* now", inline("say hello now", 3, 10, InlineFormat.Bold)!!.text)
    }

    @Test
    fun `no selection inserts the pair and puts the cursor between`() {
        val edit = inline("hi ", 3, 3, InlineFormat.Italic)!!
        assertEquals("hi __", edit.text)
        assertEquals(4, edit.selectionStart)
    }

    @Test
    fun `the cursor between an empty pair takes it out`() {
        val edit = inline("hi __", 4, 4, InlineFormat.Italic)!!
        assertEquals("hi ", edit.text)
        assertEquals(3, edit.selectionStart)
    }

    @Test
    fun `a selection that starts mid-word does nothing for bold`() {
        assertNull(inline("unbelievable", 2, 12, InlineFormat.Bold))
    }

    @Test
    fun `a selection of only whitespace does nothing`() {
        assertNull(inline("a   b", 1, 4, InlineFormat.Bold))
    }

    @Test
    fun `a second tap removes the markers`() {
        val bold = inline("say hello now", 4, 9, InlineFormat.Bold)!!
        val edit = inline(bold.text, bold.selectionStart, bold.selectionEnd, InlineFormat.Bold)!!
        assertEquals("say hello now", edit.text)
        assertEquals(4 to 9, edit.selectionStart to edit.selectionEnd)
    }

    @Test
    fun `a selection that includes the markers also removes them`() {
        assertEquals("say hello now", inline("say *hello* now", 4, 11, InlineFormat.Bold)!!.text)
    }

    @Test
    fun `a button shows as selected only when its style covers the selection`() {
        assertTrue(isInlineActive("say *hello* now", 5, 10, InlineFormat.Bold))
        assertFalse(isInlineActive("say *hello* now", 5, 10, InlineFormat.Italic))
        assertFalse(isInlineActive("say hello now", 4, 9, InlineFormat.Bold))
        assertFalse(isInlineActive("a * b *", 3, 4, InlineFormat.Bold))
    }

    @Test
    fun `styles nest`() {
        val edit = inline("*say hello*", 5, 10, InlineFormat.Italic)!!
        assertEquals("*say _hello_*", edit.text)
    }

    @Test
    fun `a protected range stops a marker inside it`() {
        val ranges = DraftRanges { listOf(ProtectedRange(0, it.length, RangeKind.Link)) }
        assertNull(toggleInline("example.com/a_b", 12, 15, InlineFormat.Italic, ranges))
    }

    @Test
    fun `a line prefix is added to every touched line and removed again`() {
        val text = "one\ntwo\nthree"
        val added = toggleLine(text, 2, 6, LineFormat.Bullet)
        assertEquals("- one\n- two\nthree", added.text)
        assertTrue(isLineActive(added.text, added.selectionStart, added.selectionEnd, LineFormat.Bullet))

        val removed = toggleLine(added.text, added.selectionStart, added.selectionEnd, LineFormat.Bullet)
        assertEquals(text, removed.text)
    }

    @Test
    fun `numbered lines count up and a bullet is replaced`() {
        assertEquals("1. a\n2. b", toggleLine("a\nb", 0, 3, LineFormat.Numbered).text)
        assertEquals("1. a", toggleLine("- a", 0, 0, LineFormat.Numbered).text)
    }

    @Test
    fun `a quote goes ahead of a list marker`() {
        assertEquals("> - a", toggleLine("- a", 0, 0, LineFormat.Quote).text)
        assertEquals("- a", toggleLine("> - a", 0, 0, LineFormat.Quote).text)
    }

    @Test
    fun `the cursor follows the prefix`() {
        val edit = toggleLine("abc", 3, 3, LineFormat.Quote)
        assertEquals("> abc", edit.text)
        assertEquals(5, edit.selectionStart)
        assertEquals(2, toggleLine("", 0, 0, LineFormat.Quote).selectionStart)
    }

    @Test
    fun `a code block is fenced and unfenced`() {
        val fenced = toggleLine("a\nb", 0, 3, LineFormat.CodeBlock)
        assertEquals("```\na\nb\n```", fenced.text)
        assertEquals("a\nb", toggleLine(fenced.text, 0, fenced.text.length, LineFormat.CodeBlock).text)
    }

    @Test
    fun `a link wraps the selection or stands alone`() {
        assertEquals(
            "see [the docs](https://a.com) now",
            insertLink("see the docs now", 4, 12, "https://a.com").text,
        )
        assertEquals("see https://a.com", insertLink("see ", 4, 4, "https://a.com").text)
    }

    @Test
    fun `consumed markers are the characters the display text lacks`() {
        val raw = "a *b* c"
        assertEquals(listOf(2..2, 4..4), consumedRanges(raw, parseTextFormat(raw, emptyList())))
        assertEquals(emptyList(), consumedRanges("plain", parseTextFormat("plain", emptyList())))
    }
}
