package com.flipcash.app.userprofile.internal.bio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BioDraftTest {

    @Test
    fun `an untouched bio has nothing to save`() {
        assertFalse(BioDraft(original = "Hello", text = "Hello").canSave)
    }

    @Test
    fun `whitespace around the same text is not a change`() {
        assertFalse(BioDraft(original = "Hello", text = "  Hello \n").canSave)
    }

    @Test
    fun `a different bio can be saved`() {
        assertTrue(BioDraft(original = "Hello", text = "Hello there").canSave)
    }

    @Test
    fun `blank over a non-blank bio is saveable because it clears the bio`() {
        assertTrue(BioDraft(original = "Hello", text = "").canSave)
        assertTrue(BioDraft(original = "Hello", text = "   ").canSave)
    }

    @Test
    fun `blank over a blank bio is not a change`() {
        assertFalse(BioDraft(original = "", text = "  ").canSave)
    }

    @Test
    fun `160 code points fit and 161 do not`() {
        val fits = BioDraft(original = "", text = "a".repeat(160))
        val over = BioDraft(original = "", text = "a".repeat(161))

        assertEquals(0, fits.remaining)
        assertTrue(fits.canSave)
        assertEquals(-1, over.remaining)
        assertFalse(over.canSave)
    }

    @Test
    fun `an emoji counts once however many chars it takes`() {
        val emoji = "😀" // one code point, two UTF-16 chars
        val draft = BioDraft(original = "", text = emoji.repeat(160))

        assertEquals(320, draft.text.length)
        assertEquals(0, draft.remaining)
        assertTrue(draft.canSave)
    }

    @Test
    fun `editing clears the error`() {
        val failed = BioDraft(original = "", text = "bad", error = BioError.Moderated)

        val edited = failed.edited("better")

        assertEquals("better", edited.text)
        assertNull(edited.error)
        assertEquals("", edited.original)
    }

    @Test
    fun `a failure keeps the text and records the error`() {
        val draft = BioDraft(original = "", text = "bad").failed(BioError.Invalid)

        assertEquals("bad", draft.text)
        assertEquals(BioError.Invalid, draft.error)
    }
}
