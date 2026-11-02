package com.flipcash.shared.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which messages get drawn as a bare jumbo emoji.
 *
 * The count is of emoji, not of characters, so the sequences that take several code points to
 * spell — a family, a flag, a keycap, a skin tone — are the cases worth pinning: each is one
 * emoji, and a naive count would put all four over the limit.
 */
class EmojiOnlyTextTest {

    private fun count(text: String) = EmojiOnlyText.clusterCountOrNull(text)

    @Test
    fun `a single emoji counts as one`() {
        assertEquals(1, count("😀"))
    }

    @Test
    fun `three emoji are still jumbo, four are not`() {
        assertEquals(3, count("😀😀😀"))
        assertNull(count("😀😀😀😀"))
    }

    @Test
    fun `spaces between emoji separate them without counting`() {
        assertEquals(2, count(" 😀 🎉 "))
    }

    @Test
    fun `a ZWJ sequence is one emoji however many code points it takes`() {
        assertEquals(1, count("👨‍👩‍👧‍👦"))
    }

    @Test
    fun `a skin tone belongs to the emoji it modifies`() {
        assertEquals(1, count("👍🏽"))
    }

    @Test
    fun `a flag is one emoji, a lone regional indicator is not text we jumbo`() {
        assertEquals(1, count("🇺🇸"))
        assertNull(count("🇺"))
    }

    @Test
    fun `a keycap is one emoji but a bare digit is a message`() {
        assertEquals(1, count("1️⃣"))
        assertNull(count("1"))
    }

    @Test
    fun `a heart with a variation selector is one emoji`() {
        assertEquals(1, count("❤️"))
    }

    @Test
    fun `any letter alongside the emoji makes it an ordinary message`() {
        assertNull(count("😀 nice"))
        assertNull(count("ok"))
        assertNull(count("!"))
    }

    @Test
    fun `a text-presentation symbol is punctuation until it asks to be an emoji`() {
        assertNull(count("©"))
        assertEquals(1, count("©️"))
    }

    @Test
    fun `an empty or blank message is not an emoji`() {
        assertNull(count(""))
        assertNull(count("   "))
    }
}
