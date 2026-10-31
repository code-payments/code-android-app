package com.flipcash.shared.chat

import com.flipcash.shared.chat.internal.MentionText
import org.junit.Test
import kotlin.test.assertEquals

class MentionTextTest {

    @Test
    fun `folds case and diacritics`() {
        assertEquals("erica", MentionText.normalize("Érica"))
        assertEquals("istanbul", MentionText.normalize("İstanbul"))
    }

    @Test
    fun `tokens are the display name's words and the handle`() {
        assertEquals(setOf("eli", "zane"), MentionText.tokens("Eli Zane", "@eli"))
    }

    @Test
    fun `the leading at is the trigger, not part of the query word`() {
        assertEquals(listOf("er", "zan"), MentionText.queryWords("@er zan"))
        assertEquals(emptyList(), MentionText.queryWords("@"))
    }
}
