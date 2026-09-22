package com.flipcash.app.messenger.internal.screens.profile.edit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The client half of `EditChatRequest.Title`'s `min_len 1, max_len 64`.
 *
 * The server enforces it too, so the stake here is not correctness of the write but what the user
 * sees: without this the Save button is live on a title the server will refuse, and the refusal
 * comes back as a generic failure rather than as the screen keeping the field for them to fix.
 */
class ChatTitleTest {

    @Test
    fun `a single character is a valid title`() {
        assertTrue(ChatTitle.isValid("a"))
    }

    @Test
    fun `exactly 64 characters is valid, 65 is not`() {
        assertTrue(ChatTitle.isValid("a".repeat(64)))
        assertFalse(ChatTitle.isValid("a".repeat(65)))
    }

    @Test
    fun `an empty title is refused`() {
        assertFalse(ChatTitle.isValid(""))
    }

    @Test
    fun `a title of only whitespace is refused`() {
        // It has a length, but every place the title is drawn would render it blank, so counting
        // the spaces would let an apparently-empty group name through.
        assertFalse(ChatTitle.isValid("    "))
    }

    @Test
    fun `surrounding whitespace does not count toward the limit`() {
        // 64 real characters padded either side still fits, because the padding is trimmed before
        // it is measured and before it is sent.
        assertTrue(ChatTitle.isValid("  " + "a".repeat(64) + "  "))
    }

    @Test
    fun `the value sent is the trimmed one`() {
        assertEquals("Book Club", ChatTitle.normalize("  Book Club  "))
    }

    @Test
    fun `length is counted in code points, not UTF-16 units`() {
        // Each of these is one code point and two Kotlin chars. 64 of them is exactly the limit at
        // the server; measuring with String.length would call it 128 and refuse a legal title.
        val emoji = "🎉" // party popper
        assertEquals(128, emoji.repeat(64).length)
        assertEquals(64, ChatTitle.lengthOf(emoji.repeat(64)))
        assertTrue(ChatTitle.isValid(emoji.repeat(64)))
        assertFalse(ChatTitle.isValid(emoji.repeat(65)))
    }
}
