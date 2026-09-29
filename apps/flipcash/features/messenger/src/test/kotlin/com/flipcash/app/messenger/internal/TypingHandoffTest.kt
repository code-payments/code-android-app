package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.screens.components.RowGap
import com.flipcash.app.messenger.internal.screens.components.gapAboveTypingRow
import com.flipcash.app.messenger.internal.screens.components.takesTypingDots
import com.flipcash.services.models.chat.MessageContent
import com.flipcash.shared.chat.models.ChatListItem
import com.flipcash.shared.chat.models.SenderIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Which arrival grows out of the typing dots, and how far the dots sit from the row above them.
 *
 * The handoff cases are the shared table in the orchestrator's `docs/cross-platform-parity.md`
 * ("typing-dots handoff with several typists"), one test per row, so iOS can check against the same
 * list. `typing` is who is typing when the arrivals land, before they clear their senders; a null
 * arrival is the viewer's own message.
 */
class TypingHandoffTest {

    @Test
    fun `a DM reply takes the dots`() {
        assertTrue(takesTypingDots(typingBefore = setOf(A), arrivals = listOf(A), typing = setOf(A)))
    }

    @Test
    fun `a DM reply during the linger takes the dots`() {
        // The linger keeps A in the set after they stopped, so it reads the same as still typing.
        assertTrue(takesTypingDots(typingBefore = setOf(A), arrivals = listOf(A), typing = setOf(A)))
    }

    @Test
    fun `a reply after the linger expired has no dots to take`() {
        assertFalse(takesTypingDots(typingBefore = emptySet(), arrivals = listOf(A), typing = emptySet()))
    }

    @Test
    fun `one of two typists sending leaves the dots to the other`() {
        assertFalse(takesTypingDots(typingBefore = setOf(A, B), arrivals = listOf(A), typing = setOf(A, B)))
    }

    @Test
    fun `a member who wasn't typing doesn't take the dots`() {
        assertFalse(takesTypingDots(typingBefore = setOf(A), arrivals = listOf(B), typing = setOf(A)))
    }

    @Test
    fun `the viewer's own message doesn't take the dots`() {
        assertFalse(takesTypingDots(typingBefore = setOf(A), arrivals = listOf(null), typing = setOf(A)))
    }

    @Test
    fun `when both typists send together the newest takes the dots`() {
        assertTrue(
            takesTypingDots(typingBefore = setOf(A, B), arrivals = listOf(A, B), typing = setOf(A, B)),
        )
    }

    @Test
    fun `a newest arrival that wasn't typing leaves the dots to exit`() {
        assertFalse(takesTypingDots(typingBefore = setOf(A), arrivals = listOf(A, B), typing = setOf(A)))
    }

    @Test
    fun `no arrivals take nothing`() {
        assertFalse(takesTypingDots(typingBefore = setOf(A), arrivals = emptyList<String?>(), typing = setOf(A)))
    }

    @Test
    fun `the dots sit a normal gap under the typist's own bubble`() {
        assertEquals(RowGap.Normal, gapAboveTypingRow(bubble(alice), listOf(alice.userId)))
    }

    @Test
    fun `the dots sit a wide gap under anyone else's bubble`() {
        assertEquals(RowGap.Wide, gapAboveTypingRow(bubble(bob), listOf(alice.userId)))
        assertEquals(RowGap.Wide, gapAboveTypingRow(bubble(null, isFromSelf = true), listOf(alice.userId)))
    }

    @Test
    fun `the dots sit a normal gap under a separator`() {
        assertEquals(RowGap.Normal, gapAboveTypingRow(ChatListItem.UnreadDivider(count = 1), listOf(alice.userId)))
    }

    private companion object {
        const val A = "A"
        const val B = "B"

        val alice = SenderIdentity(userId = listOf<Byte>(1), displayName = "Alice", picture = null)
        val bob = SenderIdentity(userId = listOf<Byte>(2), displayName = "Bob", picture = null)

        fun bubble(sender: SenderIdentity?, isFromSelf: Boolean = false) = ChatListItem.ContentBubble(
            messageId = 1L,
            contentIndex = 0,
            content = MessageContent.Text("hi"),
            isFromSelf = isFromSelf,
            timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000),
            sender = sender,
        )
    }
}
