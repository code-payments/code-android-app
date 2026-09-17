package com.flipcash.shared.chat.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A link that renders as a card is removed from the body text, so the card and the URL do not say
 * the same thing twice. The spans come from the real detector rather than hand-counted offsets —
 * the bubble cuts what the detector found, and a test that cut something else would pass while the
 * transcript left half a URL behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkSpanRemovalTest {

    private fun strip(text: String): String {
        val link = detectUrls(text).first()
        return text.withoutLinkSpan(link.start, link.end)
    }

    @Test
    fun `a message that is only a link leaves no text`() {
        assertEquals("", strip("https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"))
    }

    @Test
    fun `text before the link keeps its punctuation and loses the trailing space`() {
        assertEquals(
            "here you go:",
            strip("here you go: https://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3"),
        )
    }

    @Test
    fun `a link mid-sentence does not leave a double space behind`() {
        assertEquals("check out", strip("check https://flipcash.com out"))
    }

    @Test
    fun `a link on its own line takes the line with it`() {
        assertEquals(
            "here you go\nenjoy",
            strip("here you go\nhttps://send.flipcash.com/c/#/e=KNi8pQr1n5hRU65vKJGge3\nenjoy"),
        )
    }

    @Test
    fun `a span the text cannot hold is left alone rather than thrown on`() {
        val text = "here you go"
        assertEquals(text, text.withoutLinkSpan(start = 4, end = text.length + 10))
        assertEquals(text, text.withoutLinkSpan(start = -1, end = 3))
        assertEquals(text, text.withoutLinkSpan(start = 6, end = 2))
    }
}
