package com.flipcash.app.messenger.internal.mention

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.app.messenger.internal.screens.components.ComposerAccessory
import com.flipcash.app.messenger.internal.screens.components.composerAccessories
import com.flipcash.shared.chat.MemberMatch
import com.flipcash.shared.chat.models.ChatQuote
import com.flipcash.shared.chat.models.ChatQuoteSnippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MentionQueryTest {

    private fun token(text: String, cursor: Int = text.length) = activeMentionToken(text, TextRange(cursor))

    @Test
    fun `an at sign at the start of the text opens a mention`() {
        assertEquals(MentionToken(0, 4, "@eri"), token("@eri"))
    }

    @Test
    fun `a bare at sign opens a mention with an empty query`() {
        assertEquals(MentionToken(0, 1, "@"), token("@"))
    }

    @Test
    fun `an at sign after whitespace opens a mention`() {
        assertEquals(MentionToken(3, 7, "@eri"), token("hi @eri"))
        assertEquals(MentionToken(3, 7, "@eri"), token("hi\n@eri"))
    }

    @Test
    fun `an at sign inside a word does not`() {
        assertNull(token("a@b"))
    }

    @Test
    fun `whitespace after the word closes it`() {
        assertNull(token("hi @eri "))
    }

    @Test
    fun `a cursor moved out of the word closes it`() {
        // Back before the at sign, and into the next word.
        assertNull(token("hi @eri there", cursor = 2))
        assertNull(token("hi @eri there", cursor = 13))
    }

    @Test
    fun `the word runs only to the cursor`() {
        assertEquals(MentionToken(0, 3, "@er"), token("@erica", cursor = 3))
    }

    @Test
    fun `deleting the at sign closes it`() {
        assertNull(token("hi eri"))
    }

    @Test
    fun `a selection is not a cursor`() {
        assertNull(activeMentionToken("@eri", TextRange(1, 4)))
    }

    @Test
    fun `the fullwidth at sign opens a mention too`() {
        assertEquals(MentionToken(0, 4, "＠eri"), token("＠eri"))
    }

    @Test
    fun `a doubled at sign keeps the second for the search to reject`() {
        // The search strips exactly one, so "@@eri" searches "@eri" and finds nobody.
        assertEquals("@@eri", token("@@eri")?.text)
    }

    @Test
    fun `picking replaces exactly the word and adds one space`() {
        val text = "hey @er, see this"
        val t = token(text, cursor = 7)!!
        val (result, cursor) = insertMention(text, t, "erica")
        assertEquals("hey @erica , see this", result)
        assertEquals("hey @erica ".length, cursor)
    }

    @Test
    fun `picking at the end of the text leaves the cursor after the space`() {
        val text = "thanks @e"
        val (result, cursor) = insertMention(text, token(text)!!, "erica")
        assertEquals("thanks @erica ", result)
        assertEquals(result.length, cursor)
    }

    @Test
    fun `members without a username are not mentionable`() {
        val erica = match("Érica", "erica")
        val noHandle = match("Eric", null)
        val blank = match("Erin", "")
        assertEquals(listOf(erica), listOf(erica, noHandle, blank).mentionable())
    }

    @Test
    fun `the row cap is 4 with no reply, 3 with one, and 2 when the transcript would be squeezed`() {
        val height = { rows: Int -> 50.dp * rows }
        val roomy = 800.dp
        assertEquals(4, mentionRowCap(replyOpen = false, roomAboveComposer = roomy, listHeight = height))
        assertEquals(3, mentionRowCap(replyOpen = true, roomAboveComposer = roomy, listHeight = height))
        // 4 rows (200dp) in 300dp leaves 100dp, under the 120dp floor.
        assertEquals(2, mentionRowCap(replyOpen = false, roomAboveComposer = 300.dp, listHeight = height))
        // 3 rows (150dp) in 260dp leaves 110dp.
        assertEquals(2, mentionRowCap(replyOpen = true, roomAboveComposer = 260.dp, listHeight = height))
        // Exactly the floor is enough.
        assertEquals(4, mentionRowCap(replyOpen = false, roomAboveComposer = 320.dp, listHeight = height))
    }

    @Test
    fun `accessories stack mentions above the reply strip`() {
        val quote = ChatQuote(messageId = 1, authorName = "Ada", snippet = ChatQuoteSnippet.Text("hi"), accent = null, nameAccent = null)
        val matches = listOf(match("Érica", "erica"))
        val state = ChatViewModel.State(mentionSuggestions = matches, replyingTo = quote)
        assertEquals(
            listOf(ComposerAccessory.MentionSuggestions(matches), ComposerAccessory.Reply(quote)),
            composerAccessories(state, canType = true),
        )
        assertEquals(emptyList(), composerAccessories(state, canType = false))
    }

    private fun match(name: String, username: String?) = MemberMatch(
        userId = name.encodeToByteArray().toList(),
        displayName = name,
        username = username,
        profilePicture = null,
    )
}
