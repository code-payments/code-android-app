package com.flipcash.shared.chat.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One-for-one with iOS's `MentionDetectorTests`. Robolectric because the link cases run the real
 * [detectUrls], which reads `android.util.Patterns`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MentionDetectionTest {

    private fun handles(text: String, links: List<DetectedUrl> = emptyList()): List<String> =
        detectMentions(text, links).map { it.username }

    @Test
    fun `a handle at the start of the text is a mention`() {
        val mentions = detectMentions("@jeff hi", emptyList())
        assertEquals(listOf("jeff"), mentions.map { it.username })
        assertEquals(0 to 5, mentions.first().let { it.start to it.end })
    }

    @Test
    fun `the range covers the at sign and the handle, not the character before it`() {
        val mentions = detectMentions("ask @jeff.", emptyList())
        assertEquals(4 to 9, mentions.first().let { it.start to it.end })
    }

    @Test
    fun `uppercase letters fold to the stored lowercase handle`() {
        assertEquals(listOf("jeff_99"), handles("hey @Jeff_99"))
    }

    @Test
    fun `every handle in a message is found, in order`() {
        assertEquals(listOf("ab", "cd", "ef"), handles("@ab and @cd,@ef"))
    }

    @Test
    fun `trailing punctuation ends the handle`() {
        assertEquals(listOf("jeff"), handles("thanks @jeff!"))
        assertEquals(listOf("jeff"), handles("(@jeff)"))
    }

    @Test
    fun `an email address is not a mention`() {
        assertEquals(emptyList(), handles("write me@example.com"))
    }

    @Test
    fun `a doubled at sign is not a mention`() {
        assertEquals(emptyList(), handles("@@jeff"))
        assertEquals(emptyList(), handles("@jeff@"))
    }

    @Test
    fun `handles outside 2 to 15 characters are not mentions`() {
        assertEquals(emptyList(), handles("@a"))
        assertEquals(emptyList(), handles("@abcdefghijklmnop"))
        assertEquals(listOf("abcdefghijklmno"), handles("@abcdefghijklmno"))
    }

    @Test
    fun `characters a handle cannot hold end it rather than extend it`() {
        assertEquals(listOf("jeff"), handles("@jeff-smith"))
        assertEquals(listOf("je"), handles("@je.ff"))
    }

    @Test
    fun `a handle inside a web link is left to the link`() {
        val text = "see x.com/@jeff"
        val links = detectUrls(text)
        assertTrue(links.isNotEmpty())
        assertEquals(emptyList(), handles(text, links))
    }

    @Test
    fun `a handle beside a web link is still a mention`() {
        val text = "@jeff https://apple.com"
        assertEquals(listOf("jeff"), handles(text, detectUrls(text)))
    }

    @Test
    fun `ranges are UTF-16 offsets, past an emoji`() {
        val mentions = detectMentions("🎉 @jeff", emptyList())
        assertEquals(3 to 8, mentions.first().let { it.start to it.end })
    }

    @Test
    fun `plain text has no mentions`() {
        assertEquals(emptyList(), handles("no handles here"))
        assertEquals(emptyList(), handles("just an @ sign"))
    }

    // Text-format spec, rule 6: one `_` may sit before the `@`, and then the handle's final `_`
    // is the italic's closer rather than part of the name.
    @Test
    fun `an italic mention gives its closing underscore back`() {
        val text = "_@jeff_"
        val mention = detectMentions(text, emptyList()).single()
        assertEquals("jeff", mention.username)
        assertEquals(1 to 6, mention.start to mention.end)
    }

    @Test
    fun `a handle that really ends in an underscore is written with two`() {
        assertEquals(listOf("jeff_"), handles("_@jeff__"))
    }

    @Test
    fun `nothing is trimmed when the handle does not end in an underscore`() {
        assertEquals(listOf("jeff_x"), handles("_@jeff_x"))
    }

    @Test
    fun `an underscore that follows a handle character is not an opener`() {
        assertEquals(emptyList(), handles("jeff_@gmail.com"))
        assertEquals(emptyList(), handles("__@jeff__"))
    }

    @Test
    fun `an italic that ends in a mention keeps the underscore in the handle`() {
        assertEquals(listOf("jeff_"), handles("_hey @jeff_"))
    }
}
